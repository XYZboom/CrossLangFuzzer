package com.github.xyzboom.codesmith.ir

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SccIncrementalPartitionPlanTest {
    @Test
    fun `SCC atomic and refined plans preserve dependency components and reach full program`() {
        val generated = generatedSccTestProgram()
        val program = generated.program
        val generatedCycleClasses = createGeneratedMultiClassScc(program)
        val first = program.sccIncrementalPartitionPlan(seed = 31)
        val replay = program.sccIncrementalPartitionPlan(seed = 31)

        val components = first.components.map { component -> component.map { it.name }.toSet() }
        assertTrue(components.isNotEmpty())
        assertTrue(program.modules.size == 1)
        assertTrue(components.any { it.containsAll(generatedCycleClasses) })
        assertEquals(
            first.refinedEdits.map(::editDescription),
            replay.refinedEdits.map(::editDescription),
            "A fixed seed must reproduce the edit script",
        )

        assertEquals(components, first.sccBatches.map { it.classes.map { clazz -> clazz.name }.toSet() })
        val addSccEdits = first.refinedEdits.filterIsInstance<SccIncrementalEdit.AddScc>()
        assertEquals(components, addSccEdits.map { it.classes.map { clazz -> clazz.name }.toSet() })
        assertTrue(addSccEdits.any { edit -> edit.classes.map { it.name }.containsAll(generatedCycleClasses) })
        assertEquals(program.classes.map { it.name }.toSet(), addSccEdits.flatMap { it.classes }.map { it.name }.toSet())
        assertEquals(program.classes.size, addSccEdits.sumOf { it.classes.size }, "Each class must be introduced once")
        val orderedClasses = linkedSetOf<com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration>()
        val dependencies = program.classDependencyGraph
        first.components.forEach { component ->
            assertTrue(
                component.all { clazz -> dependencies.getValue(clazz).all { it in component || it in orderedClasses } },
                "The randomized SCC order must remain dependency-first",
            )
            orderedClasses += component
        }
        val addedClasses = linkedSetOf<String>()
        first.refinedSnapshots.forEach { snapshot ->
            when (val edit = snapshot.edit) {
                is SccIncrementalEdit.AddScc -> {
                    addedClasses += edit.classes.map { it.name }
                    assertEquals(addedClasses, snapshot.includedClassNames.toSet(), "An SCC must enter atomically")
                    assertTrue(snapshot.parameterCounts.isEmpty(), "SCC insertion must preserve complete signatures")
                }
                is SccIncrementalEdit.TruncateFunctionParameters -> {
                    assertEquals(addedClasses, snapshot.includedClassNames.toSet())
                    assertEquals(edit.parameterCount, snapshot.parameterCounts[SccFunctionKey(edit.className, edit.functionIndex)])
                }
                is SccIncrementalEdit.AddFunctionParameter -> {
                    assertEquals(addedClasses, snapshot.includedClassNames.toSet())
                    val function = program.classes.single { it.name == edit.className }.functions[edit.functionIndex]
                    if (edit.parameterCount < function.parameterList.parameters.size) {
                        assertEquals(edit.parameterCount, snapshot.parameterCounts[SccFunctionKey(edit.className, edit.functionIndex)])
                    } else {
                        assertTrue(SccFunctionKey(edit.className, edit.functionIndex) !in snapshot.parameterCounts)
                    }
                }
            }
        }
        assertTrue(first.refinedEdits.any { it is SccIncrementalEdit.TruncateFunctionParameters })
        assertTrue(first.refinedEdits.any { it is SccIncrementalEdit.AddFunctionParameter })
        assertEquals(
            program.classes.map { it.name }.toSet(),
            first.refinedSnapshots.last().includedClassNames.toSet(),
        )

        val finalSnapshot = first.refinedSnapshots.last()
        val finalProgram = finalSnapshot.materialize(program)
        assertEquals(program.classes.map { it.name }.toSet(), finalProgram.classes.map { it.name }.toSet())
        assertEquals(
            program.classes.sumOf { clazz -> clazz.functions.sumOf { it.parameterList.parameters.size } },
            finalProgram.classes.sumOf { clazz -> clazz.functions.sumOf { it.parameterList.parameters.size } },
            "The final parameter-level snapshot must restore every parameter",
        )
    }

    @Test
    fun `automatic stages balance dependency ordered SCC batches without splitting a component`() {
        val program = generatedSccTestProgram().program
        val multiClassComponent = createGeneratedMultiClassScc(program)
        val plan = program.sccIncrementalPartitionPlan(seed = 53)
        val stages = plan.sccStages(requestedStageCount = 3)

        assertEquals(minOf(3, plan.sccBatches.size), stages.size)
        assertTrue(stages.all { it.isNotEmpty() }, "Automatic partitions must not create empty stages")
        val stageBatchCounts = stages.map { it.size }
        assertTrue(stageBatchCounts.max() - stageBatchCounts.min() <= 1, "SCC batches must be balanced across stages")
        assertEquals(plan.sccBatches, stages.flatten(), "Stage grouping must preserve dependency-first SCC order")
        val cycleStages = stages.filter { stage ->
            stage.flatMap { it.classes }.any { it.name in multiClassComponent }
        }
        assertEquals(1, cycleStages.size, "A multi-class SCC must remain wholly inside one stage")
        assertTrue(
            cycleStages.single().flatMap { it.classes }.map { it.name }.containsAll(multiClassComponent),
            "All generated cycle classes must be added in the same automatic stage",
        )
    }

    @Test
    fun `single-class SCC remains one SCC edit while parameters can still be refined`() {
        val program = generatedSccTestProgram().program
        val plan = program.sccIncrementalPartitionPlan(seed = 7)
        val singletons = plan.components.filter { it.size == 1 }
        assertTrue(singletons.isNotEmpty())
        assertTrue(
            singletons.all { component ->
                plan.refinedEdits.filterIsInstance<SccIncrementalEdit.AddScc>()
                    .count { edit -> edit.classes.any { it.name == component.single().name } } == 1
            },
        )
        assertTrue(plan.refinedEdits.filterIsInstance<SccIncrementalEdit.AddFunctionParameter>().isNotEmpty())
    }

    private fun editDescription(edit: SccIncrementalEdit): String = when (edit) {
        is SccIncrementalEdit.AddScc ->
            "scc:${edit.componentIndex}:${edit.classes.map { it.name }}"
        is SccIncrementalEdit.TruncateFunctionParameters ->
            "truncate:${edit.componentIndex}:${edit.className}:${edit.functionIndex}:${edit.parameterCount}"
        is SccIncrementalEdit.AddFunctionParameter ->
            "parameter:${edit.componentIndex}:${edit.className}:${edit.functionIndex}:${edit.parameterCount}"
    }

}
