package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.generator.IrDeclGenerator
import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.classes
import com.github.xyzboom.codesmith.ir.functions
import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration
import com.github.xyzboom.codesmith.ir.impl.IrProgramImpl
import com.github.xyzboom.codesmith.ir.types.IrDefinitelyNotNullType
import com.github.xyzboom.codesmith.ir.types.IrNullableType
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrPlatformType
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.github.xyzboom.codesmith.serde.gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SerializationTest {

    @Test
    fun `program JSON round trip preserves printed sources for generic IR`() {
        val coverage = mutableMapOf<String, Int>()

        for (seed in 20270000..20270099) {
            val program = IrDeclGenerator(random = Random(seed)).genProgram()
            val classes = program.classes.flatMap { it.allNestedClasses() }
            val functions = program.functions + classes.flatMap { it.functions }
            val typeParameters = classes.flatMap { it.typeParameters } + functions.flatMap { it.typeParameters }

            val json = gson.toJson(program, IrProgramImpl::class.java)
            val jsonTree = JsonParser.parseString(json)
            assertFalse(jsonTree.containsField("printNullableAnnotations"), "obsolete IR state serialized for seed $seed")
            assertFalse(jsonTree.containsFalseBooleanField("isFinal"), "default false state should be omitted for seed $seed")

            val originalSources = IrProgramPrinter().print(program)
            val restored = gson.fromJson(json, IrProgram::class.java)
            val restoredSources = IrProgramPrinter().print(restored)
            assertEquals(originalSources, restoredSources, "printed source files changed after IR round trip for seed $seed")

            if (classes.any { it.typeParameters.isNotEmpty() }) coverage.putIfAbsent("generic class", seed)
            if (classes.any { it.usesOwnTypeParameterInFunctions() }) coverage.putIfAbsent("class type parameter use", seed)
            if (functions.any { it.typeParameters.isNotEmpty() }) coverage.putIfAbsent("generic function", seed)
            if (functions.any { !it.isFinal }) coverage.putIfAbsent("omitted default function state", seed)
            if (typeParameters.any { it.upperbound !== IrAny }) coverage.putIfAbsent("non-default type parameter bound", seed)
            if (classes.any { it.classKind != ClassKind.INTERFACE && it.superType === IrAny }) {
                coverage.putIfAbsent("default supertype", seed)
            }
            if (functions.any { it.usesOwnTypeParameter() }) coverage.putIfAbsent("type parameter use", seed)
            if (functions.any { it.hasNullableTypeParameterUse() }) {
                coverage.putIfAbsent("nullable type parameter use", seed)
            }
            if (functions.any { it.isOverride && it.override.isNotEmpty() }) coverage.putIfAbsent("override", seed)
        }

        assertEquals(
            setOf(
                "generic class",
                "class type parameter use",
                "generic function",
                "omitted default function state",
                "non-default type parameter bound",
                "default supertype",
                "type parameter use",
                "nullable type parameter use",
                "override",
            ),
            coverage.keys,
            "round-trip feature coverage was not observed in these deterministic seeds: $coverage",
        )
    }

    @Test
    fun `function JSON omits the removed nullable annotation state`() {
        val function = buildFunctionDeclaration {
            name = "simple"
            parameterList = buildParameterList()
        }
        val json = gson.toJson(function, IrFunctionDeclaration::class.java)
        assertFalse(JsonParser.parseString(json).asJsonObject.has("printNullableAnnotations"))
    }

    private fun JsonElement.containsField(fieldName: String): Boolean = when {
        isJsonObject -> asJsonObject.entrySet().any { (name, value) -> name == fieldName || value.containsField(fieldName) }
        isJsonArray -> asJsonArray.any { it.containsField(fieldName) }
        else -> false
    }

    private fun JsonElement.containsFalseBooleanField(fieldName: String): Boolean = when {
        isJsonObject -> asJsonObject.entrySet().any { (name, value) ->
            (name == fieldName && value.isJsonPrimitive && value.asJsonPrimitive.isBoolean && !value.asBoolean) ||
                value.containsFalseBooleanField(fieldName)
        }
        isJsonArray -> asJsonArray.any { it.containsFalseBooleanField(fieldName) }
        else -> false
    }

    private fun IrClassDeclaration.allNestedClasses(): List<IrClassDeclaration> =
        listOf(this) + classes.flatMap { it.allNestedClasses() }

    private fun IrClassDeclaration.usesOwnTypeParameterInFunctions(): Boolean {
        val ownNames = typeParameters.map { it.name }.toSet()
        if (ownNames.isEmpty()) return false
        return functions.any { function ->
            val usedNames = function.parameterList.parameters.flatMap { it.type.referencedTypeParameterNames() }
                .toSet() + function.returnType.referencedTypeParameterNames()
            ownNames.any { it in usedNames }
        }
    }

    private fun IrFunctionDeclaration.usesOwnTypeParameter(): Boolean {
        val ownNames = typeParameters.map { it.name }.toSet()
        if (ownNames.isEmpty()) return false
        val usedNames = parameterList.parameters.flatMap { it.type.referencedTypeParameterNames() }
            .toSet() + returnType.referencedTypeParameterNames()
        return ownNames.any { it in usedNames }
    }

    private fun IrFunctionDeclaration.hasNullableTypeParameterUse(): Boolean =
        (parameterList.parameters.map { it.type } + returnType).any { it.containsNullableTypeParameter() }

    private fun IrType.referencedTypeParameterNames(): Set<String> = when (this) {
        is IrTypeParameter -> setOf(name)
        is IrNullableType -> innerType.referencedTypeParameterNames()
        is IrDefinitelyNotNullType -> innerType.referencedTypeParameterNames()
        is IrPlatformType -> innerType.referencedTypeParameterNames()
        is IrParameterizedClassifier -> arguments.values.flatMap { (_, argument) ->
            argument.type?.referencedTypeParameterNames().orEmpty()
        }.toSet()
        else -> emptySet()
    }

    private fun IrType.containsNullableTypeParameter(): Boolean = when (this) {
        is IrNullableType -> innerType is IrTypeParameter || innerType.containsNullableTypeParameter()
        is IrDefinitelyNotNullType -> innerType.containsNullableTypeParameter()
        is IrPlatformType -> innerType.containsNullableTypeParameter()
        is IrParameterizedClassifier -> arguments.values.any { (_, argument) ->
            argument.type?.containsNullableTypeParameter() == true
        }
        else -> false
    }
}
