package com.github.xyzboom.codesmith.kotlin

import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.IrDeclGenerator
import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.classes
import com.github.xyzboom.codesmith.ir.deepCopy
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration
import com.github.xyzboom.codesmith.ir.types.type
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class K2IncrementalStateMutationTest {
    @Test
    fun `source path relocation preserves IR and source text`() {
        val logicalSources = mapOf("Api.kt" to "class Api {}\n")
        val relocated = applySourceLayout(logicalSources, mapOf("Api.kt" to "moved/Api.kt"))

        assertEquals(mapOf("moved/Api.kt" to "class Api {}\n"), relocated)
        assertEquals(logicalSources.values.single(), relocated.values.single())
    }

    @Test
    fun `interface method body toggle is available when Java implements the interface`() {
        val interfaceFunction = buildFunctionDeclaration {
            name = "method"
            language = Language.KOTLIN
            parameterList = buildParameterList()
            body = null
        }
        val kotlinInterface = buildClassDeclaration {
            name = "KotlinApi"
            language = Language.KOTLIN
            classKind = ClassKind.INTERFACE
            functions += interfaceFunction
        }
        val javaImplementation = buildClassDeclaration {
            name = "JavaImplementation"
            language = Language.JAVA
            classKind = ClassKind.FINAL
            implementedTypes += kotlinInterface.type
        }
        val program = buildProgram {
            classes += kotlinInterface
            classes += javaImplementation
        }
        val state = K2IncrementalFuzzState(program)

        val mutation = K2IncrementalStateMutationScheduler(481).next(
            state = state,
            logicalSources = IrProgramPrinter().print(program),
            outputClassFiles = emptySet(),
            stepIndex = 0,
        ) ?: error("Expected a Java-compatible interface method body toggle")

        assertEquals("TOGGLE_INTERFACE_METHOD_BODY", mutation.operator)
        assertTrue(interfaceFunction.body == null, "Mutation must leave the original program unchanged")
        assertNotNull(mutation.state.program.classes.single { it.name == "KotlinApi" }.functions.single().body)
    }

    @Test
    fun `weighted declaration deletions include parameter generic argument function and class levels`() {
        val config = GeneratorConfig(
            javaRatio = 0.0f,
            topLevelDeclRange = 16..16,
            topLevelClassWeight = 1,
            topLevelFunctionWeight = 0,
            topLevelPropertyWeight = 0,
            classHasSuperProbability = 0.6f,
            classImplNumRange = 0..2,
            classMemberNumRange = 3..5,
            classMemberIsFunctionWeight = 1,
            classMemberIsPropertyWeight = 0,
            classHasTypeParameterProbability = 1.0f,
            classTypeParameterNumberRange = 1..2,
            functionParameterNumRange = 1..3,
            functionHasTypeParameterProbability = 0.0f,
            enableUseSiteVariance = false,
            moduleNumRange = 1..1,
        )
        val program = IrDeclGenerator(config, Random(9137), Language.KOTLIN).genProgram()
        val scheduler = K2IncrementalStateMutationScheduler(481)
        val outputClasses = program.classes.map { "kotlin/${it.name}.class" }.toSet()
        val seenOperators = mutableSetOf<String>()
        var state = K2IncrementalFuzzState(program)

        repeat(48) { step ->
            val sources = IrProgramPrinter().print(state.program)
            val mutation = scheduler.next(state, sources, outputClasses, step) ?: return@repeat
            seenOperators += mutation.operator
            state = mutation.state
        }

        assertTrue("REMOVE_FUNCTION_PARAMETER" in seenOperators, "Expected parameter-level deletion, got $seenOperators")
        assertTrue("REMOVE_GENERIC_ARGUMENT" in seenOperators, "Expected generic-argument deletion, got $seenOperators")
        assertTrue("REMOVE_FUNCTION" in seenOperators, "Expected function-level deletion, got $seenOperators")
        assertTrue("REMOVE_UNREFERENCED_CLASS" in seenOperators, "Expected class-level deletion, got $seenOperators")
    }

    @Test
    fun `generated program supports replayable local state changes`() {
        val config = GeneratorConfig(
            javaRatio = 0.0f,
            topLevelDeclRange = 8..8,
            topLevelClassWeight = 1,
            topLevelFunctionWeight = 0,
            topLevelPropertyWeight = 0,
            classHasSuperProbability = 0.0f,
            classImplNumRange = 0..0,
            classMemberNumRange = 2..3,
            classMemberIsFunctionWeight = 1,
            classMemberIsPropertyWeight = 0,
            classHasTypeParameterProbability = 0.0f,
            functionParameterNumRange = 1..2,
            functionHasTypeParameterProbability = 0.0f,
            enableUseSiteVariance = false,
            moduleNumRange = 1..1,
        )
        val original = IrDeclGenerator(config, Random(9137), Language.KOTLIN).genProgram()
        assertEquals(1, original.modules.size)
        assertTrue(original.classes.size >= 2)
        original.classes.first().classKind = ClassKind.INTERFACE
        original.classes.first().functions.firstOrNull()?.let { function ->
            function.body = null
        }
        val originalJson = com.github.xyzboom.codesmith.serde.gson.toJson(original)
        val state = K2IncrementalFuzzState(original.deepCopy())
        val sources = IrProgramPrinter().print(state.program)
        val classFiles = state.program.classes.map { "kotlin/${it.name}.class" }.toSet()
        val scheduler = K2IncrementalStateMutationScheduler(481)
        val seenOperators = linkedSetOf<String>()
        var currentState = state

        repeat(30) { step ->
            val logicalSources = IrProgramPrinter().print(currentState.program)
            val mutation = scheduler.next(currentState, logicalSources, classFiles, step) ?: return@repeat
            seenOperators += mutation.operator
            assertTrue(mutation.state.program.modules.single().classes.isNotEmpty())
            currentState = mutation.state
        }

        assertEquals(originalJson, com.github.xyzboom.codesmith.serde.gson.toJson(original), "Mutations must not change the generated baseline IR")
        assertTrue("MODIFY_FUNCTION_PARAMETER_TYPE" in seenOperators, "Expected a member-signature mutation, got $seenOperators")
        assertTrue("REMOVE_FUNCTION" in seenOperators, "Expected a member deletion, got $seenOperators")
        assertTrue("REMOVE_UNREFERENCED_CLASS" in seenOperators, "Expected a class/file deletion, got $seenOperators")
        assertTrue("MOVE_SOURCE_FILE" in seenOperators, "Expected a path-only source move, got $seenOperators")
        assertTrue("DELETE_INCREMENTAL_OUTPUT_CLASS" in seenOperators, "Expected an incremental output deletion, got $seenOperators")

        val mappedSources = applySourceLayout(sources, currentState.sourceLayout)
        assertEquals(sources.size, mappedSources.size)
        assertFalse(mappedSources.isEmpty())
        assertNotNull(currentState.program)
    }
}
