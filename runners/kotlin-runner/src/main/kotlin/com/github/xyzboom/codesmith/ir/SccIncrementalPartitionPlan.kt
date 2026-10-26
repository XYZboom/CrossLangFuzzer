package com.github.xyzboom.codesmith.ir

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import kotlin.random.Random

/** One source update batch; batches never cross an SCC boundary. */
data class SccClassBatch(
    val componentIndex: Int,
    val classes: List<IrClassDeclaration>,
)

/** Identifies a function whose parameter list is temporarily changed in a snapshot. */
data class SccFunctionKey(val className: String, val functionIndex: Int)

/** Replayable source-state edit; SCC additions are atomic and parameter edits are separate. */
sealed interface SccIncrementalEdit {
    val componentIndex: Int

    data class AddScc(
        override val componentIndex: Int,
        val classes: List<IrClassDeclaration>,
    ) : SccIncrementalEdit

    data class TruncateFunctionParameters(
        override val componentIndex: Int,
        val className: String,
        val functionIndex: Int,
        val parameterCount: Int,
    ) : SccIncrementalEdit

    data class AddFunctionParameter(
        override val componentIndex: Int,
        val className: String,
        val functionIndex: Int,
        val parameterCount: Int,
    ) : SccIncrementalEdit
}

/** One materializable compiler state in the planned incremental edit sequence. */
data class SccIncrementalSnapshot(
    val includedClassNames: List<String>,
    /** Only functions whose parameter lists are temporarily truncated. */
    val parameterCounts: Map<SccFunctionKey, Int>,
    val edit: SccIncrementalEdit,
)

/**
 * Two update schedules for one single-module program. Both add dependency-first
 * SCCs atomically with complete signatures; the refined schedule then truncates
 * selected parameter lists and restores them one parameter at a time.
 */
data class SccIncrementalPartitionPlan(
    val components: List<List<IrClassDeclaration>>,
    val sccBatches: List<SccClassBatch>,
    val refinedEdits: List<SccIncrementalEdit>,
    val refinedSnapshots: List<SccIncrementalSnapshot>,
) {
    fun snapshots(batches: List<SccClassBatch>): List<List<IrClassDeclaration>> {
        val included = linkedSetOf<IrClassDeclaration>()
        return batches.map { batch ->
            included.addAll(batch.classes)
            included.toList()
        }
    }

    /** Group dependency-first SCC batches into contiguous, balanced stages without splitting an SCC. */
    fun sccStages(requestedStageCount: Int): List<List<SccClassBatch>> {
        require(requestedStageCount > 0) { "SCC stage count must be positive" }
        if (sccBatches.isEmpty()) return emptyList()
        val stageCount = minOf(requestedStageCount, sccBatches.size)
        return List(stageCount) { stageIndex ->
            val start = stageIndex * sccBatches.size / stageCount
            val end = (stageIndex + 1) * sccBatches.size / stageCount
            // stageCount <= batch count, so integer partition boundaries must leave each stage nonempty.
            check(start < end) { "SCC stage partition unexpectedly produced an empty stage" }
            sccBatches.subList(start, end)
        }
    }

}

/**
 * Build seeded dependency-first SCC batches and a separate parameter-stress
 * schedule. SCC insertion always uses complete declarations and signatures;
 * parameter lists are truncated only in later, explicitly marked edits.
 */
fun IrProgram.sccIncrementalPartitionPlan(seed: Int): SccIncrementalPartitionPlan {
    require(modules.size == 1) { "SCC incremental plans currently require exactly one module" }
    val programClasses = classes
    require(programClasses.map { it.name }.distinct().size == programClasses.size) {
        "SCC incremental source planning requires unique class names"
    }

    val random = Random(seed)
    val components = seededDependencyFirstComponents(
        topologicallyOrderedClassComponents,
        classDependencyGraph,
        random,
    )
    val atomic = components.mapIndexed { index, component -> SccClassBatch(index, component) }
    val edits = mutableListOf<SccIncrementalEdit>()
    val snapshots = mutableListOf<SccIncrementalSnapshot>()
    val included = linkedSetOf<String>()
    val parameterCounts = linkedMapOf<SccFunctionKey, Int>()

    components.forEachIndexed { componentIndex, component ->
        val orderedClasses = if (component.size == 1) component else component.shuffled(random)
        val refinedFunctionsByClass = linkedMapOf<IrClassDeclaration, Set<Int>>()
        for (clazz in orderedClasses) {
            val eligibleFunctions = clazz.functions.indices.filter {
                clazz.functions[it].parameterList.parameters.isNotEmpty()
            }
            val mandatoryOverrides = eligibleFunctions.filter { clazz.functions[it].isOverride }
            val optionalFunctions = eligibleFunctions - mandatoryOverrides.toSet()
            val refinedFunctions = if (mandatoryOverrides.isNotEmpty()) {
                val optionalCount = if (optionalFunctions.isEmpty()) 0
                else random.nextInt(from = 0, until = optionalFunctions.size + 1)
                mandatoryOverrides + optionalFunctions.shuffled(random).take(optionalCount)
            } else if (eligibleFunctions.isEmpty()) {
                emptySet()
            } else {
                val count = random.nextInt(from = 1, until = eligibleFunctions.size + 1)
                eligibleFunctions.shuffled(random).take(count).toSet()
            }
            refinedFunctionsByClass[clazz] = refinedFunctions.toSet()
        }

        included += component.map { it.name }
        val addScc = SccIncrementalEdit.AddScc(componentIndex, component)
        edits += addScc
        snapshots += SccIncrementalSnapshot(included.toList(), parameterCounts.toMap(), addScc)

        for (clazz in orderedClasses) {
            for (functionIndex in refinedFunctionsByClass.getValue(clazz).shuffled(random)) {
                val key = SccFunctionKey(clazz.name, functionIndex)
                val fullParameterCount = clazz.functions[functionIndex].parameterList.parameters.size
                val truncatedParameterCount = random.nextInt(from = 0, until = fullParameterCount)
                parameterCounts[key] = truncatedParameterCount
                val truncate = SccIncrementalEdit.TruncateFunctionParameters(
                    componentIndex = componentIndex,
                    className = clazz.name,
                    functionIndex = functionIndex,
                    parameterCount = truncatedParameterCount,
                )
                edits += truncate
                snapshots += SccIncrementalSnapshot(included.toList(), parameterCounts.toMap(), truncate)

                for (count in truncatedParameterCount + 1..fullParameterCount) {
                    if (count == fullParameterCount) parameterCounts.remove(key)
                    else parameterCounts[key] = count
                    val addParameter = SccIncrementalEdit.AddFunctionParameter(
                        componentIndex = componentIndex,
                        className = clazz.name,
                        functionIndex = functionIndex,
                        parameterCount = count,
                    )
                    edits += addParameter
                    snapshots += SccIncrementalSnapshot(included.toList(), parameterCounts.toMap(), addParameter)
                }
            }
        }
    }
    return SccIncrementalPartitionPlan(components, atomic, edits, snapshots)
}

private fun seededDependencyFirstComponents(
    components: List<List<IrClassDeclaration>>,
    dependencies: Map<IrClassDeclaration, Set<IrClassDeclaration>>,
    random: Random,
): List<List<IrClassDeclaration>> {
    val componentOf = buildMap {
        components.forEachIndexed { index, component -> component.forEach { put(it, index) }
        }
    }
    val componentDependencies = components.mapIndexed { index, component ->
        component.flatMap { dependencies.getValue(it) }
            .map { dependency -> componentOf.getValue(dependency) }
            .filter { dependencyIndex -> dependencyIndex != index }
            .toSet()
    }
    val remaining = components.indices.toMutableSet()
    return buildList {
        while (remaining.isNotEmpty()) {
            val ready = remaining.filter { index -> componentDependencies[index].none(remaining::contains) }
            check(ready.isNotEmpty()) { "SCC condensation graph unexpectedly contains a cycle" }
            val selected = ready[random.nextInt(ready.size)]
            add(components[selected])
            remaining.remove(selected)
        }
    }
}
