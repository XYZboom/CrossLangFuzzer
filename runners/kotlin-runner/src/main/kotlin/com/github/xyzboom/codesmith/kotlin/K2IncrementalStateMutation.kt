package com.github.xyzboom.codesmith.kotlin

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.classes
import com.github.xyzboom.codesmith.ir.classDependencyGraph
import com.github.xyzboom.codesmith.ir.deepCopy
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.expressions.builder.buildBlock
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeArgument
import com.github.xyzboom.codesmith.ir.types.IrTypeContainer
import com.github.xyzboom.codesmith.ir.types.IrTypeParameterName
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import java.nio.file.Path
import kotlin.random.Random

internal data class K2IncrementalFuzzState(
    val program: IrProgram,
    /** Maps printer-derived logical source paths to their current relative paths. */
    val sourceLayout: Map<String, String> = emptyMap(),
)

internal data class K2IncrementalOutputAction(
    val deleteRelativePath: String,
)

internal data class K2IncrementalStateMutation(
    val operator: String,
    val description: String,
    val state: K2IncrementalFuzzState,
    val outputAction: K2IncrementalOutputAction? = null,
)

/**
 * Applies local, replayable mutations to one generator-produced program. No
 * program is regenerated between steps; only the current state is retained.
 */
internal class K2IncrementalStateMutationScheduler(seed: Int) {
    private val random = Random(seed)
    private val operators = listOf(
        MutationOperator("MODIFY_FUNCTION_PARAMETER_TYPE", 1, ::modifyFunctionParameterType),
        MutationOperator("TOGGLE_INTERFACE_METHOD_BODY", 1, ::toggleInterfaceMethodBody),
        MutationOperator("REMOVE_FUNCTION_PARAMETER", 4, ::removeFunctionParameter),
        MutationOperator("REMOVE_GENERIC_ARGUMENT", 3, ::removeGenericArgument),
        MutationOperator("REMOVE_FUNCTION", 2, ::removeFunction),
        MutationOperator("REMOVE_UNREFERENCED_CLASS", 1, ::removeUnreferencedClass),
        MutationOperator("MOVE_SOURCE_FILE", 1, ::moveKotlinSourceFile),
        MutationOperator("DELETE_INCREMENTAL_OUTPUT_CLASS", 1, ::deleteKotlinOutputClass),
    )
    private val weightedOperators = operators.flatMap { operator -> List(operator.weight) { operator } }
    private val operatorOffset = random.nextInt(weightedOperators.size)

    fun next(
        state: K2IncrementalFuzzState,
        logicalSources: Map<String, String>,
        outputClassFiles: Set<String>,
        stepIndex: Int,
    ): K2IncrementalStateMutation? {
        val startIndex = (operatorOffset + stepIndex) % weightedOperators.size
        val orderedOperators = buildList {
            val seenNames = mutableSetOf<String>()
            if (stepIndex == 0) {
                operators.firstOrNull { it.name == "TOGGLE_INTERFACE_METHOD_BODY" }?.let {
                    add(it)
                    seenNames += it.name
                }
            }
            for (offset in weightedOperators.indices) {
                val candidate = weightedOperators[(startIndex + offset) % weightedOperators.size]
                if (seenNames.add(candidate.name)) add(candidate)
            }
        }
        for (operator in orderedOperators) {
            val result = operator.apply(state, logicalSources, outputClassFiles, stepIndex)
            if (result != null) return result
        }
        return null
    }

    private data class MutationOperator(
        val name: String,
        val weight: Int,
        val apply: (K2IncrementalFuzzState, Map<String, String>, Set<String>, Int) -> K2IncrementalStateMutation?,
    )

    private fun modifyFunctionParameterType(
        state: K2IncrementalFuzzState,
        unusedLogicalSources: Map<String, String>,
        unusedOutputClassFiles: Set<String>,
        unusedStepIndex: Int,
    ): K2IncrementalStateMutation? {
        val classes = state.program.classes
        val overriddenFunctions = classes.flatMap { it.functions }.flatMap { function ->
            function.override.map { it.containingClassName to it.name }
        }.toSet()
        val candidates = classes.flatMap { clazz ->
            if (clazz.language != Language.KOTLIN) return@flatMap emptyList()
            clazz.functions.flatMapIndexed { functionIndex, function ->
                if (function.isOverride || (clazz.name to function.name) in overriddenFunctions) {
                    emptyList()
                } else {
                    function.parameterList.parameters.mapIndexedNotNull { parameterIndex, parameter ->
                        if (parameter.type == IrAny) null else ParameterTarget(clazz.name, functionIndex, parameterIndex, parameter.type::class.simpleName)
                    }
                }
            }
        }
        val target = candidates.randomOrNull(random) ?: return null
        val updated = state.program.deepCopy()
        val function = updated.classes.single { it.name == target.className }.functions[target.functionIndex]
        function.parameterList.parameters[target.parameterIndex].type = IrAny
        return K2IncrementalStateMutation(
            operator = "MODIFY_FUNCTION_PARAMETER_TYPE",
            description = "${target.className}.${function.name}.parameter[${target.parameterIndex}]:${target.oldTypeName}->Any",
            state = state.copy(program = updated),
        )
    }

    private fun toggleInterfaceMethodBody(
        state: K2IncrementalFuzzState,
        unusedLogicalSources: Map<String, String>,
        unusedOutputClassFiles: Set<String>,
        unusedStepIndex: Int,
    ): K2IncrementalStateMutation? {
        val javaImplementedKotlinInterfaces = state.program.classes
            .filter { it.language == Language.JAVA }
            .flatMap { it.implementedTypes.filterIsInstance<com.github.xyzboom.codesmith.ir.types.IrClassifier>() }
            .map { it.classDecl }
            .toSet()
        val candidates = state.program.classes.flatMap { clazz ->
            if (clazz.language != Language.KOTLIN || clazz.classKind != ClassKind.INTERFACE ||
                clazz !in javaImplementedKotlinInterfaces
            ) {
                emptyList()
            } else {
                clazz.functions.mapIndexedNotNull { functionIndex, function ->
                    val emptyOrAbstract = function.body == null || function.body?.expressions?.isEmpty() == true
                    if (function.isOverrideStub || !emptyOrAbstract) null else InterfaceFunctionTarget(clazz.name, functionIndex, function.name, function.body == null)
                }
            }
        }
        val target = candidates.randomOrNull(random) ?: return null
        val updated = state.program.deepCopy()
        val function = updated.classes.single { it.name == target.className }.functions[target.functionIndex]
        function.body = if (target.wasAbstract) buildBlock() else null
        return K2IncrementalStateMutation(
            operator = "TOGGLE_INTERFACE_METHOD_BODY",
            description = "${target.className}.${target.functionName}:${if (target.wasAbstract) "ABSTRACT" else "DEFAULT"}->${if (target.wasAbstract) "DEFAULT" else "ABSTRACT"}",
            state = state.copy(program = updated),
        )
    }

    private fun removeFunction(
        state: K2IncrementalFuzzState,
        unusedLogicalSources: Map<String, String>,
        unusedOutputClassFiles: Set<String>,
        unusedStepIndex: Int,
    ): K2IncrementalStateMutation? {
        val classes = state.program.classes
        val overriddenFunctions = classes.flatMap { it.functions }.flatMap { function ->
            function.override.map { it.containingClassName to it.name }
        }.toSet()
        val candidates = classes.flatMap { clazz ->
            if (clazz.language != Language.KOTLIN) return@flatMap emptyList()
            clazz.functions.mapIndexedNotNull { functionIndex, function ->
                if (!function.isOverride && (clazz.name to function.name) !in overriddenFunctions && !function.isOverrideStub) {
                    FunctionTarget(clazz.name, functionIndex, function.name)
                } else null
            }
        }
        val target = candidates.randomOrNull(random) ?: return null
        val updated = state.program.deepCopy()
        val clazz = updated.classes.single { it.name == target.className }
        clazz.functions.removeAt(target.functionIndex)
        return K2IncrementalStateMutation(
            operator = "REMOVE_FUNCTION",
            description = "${target.className}.${target.functionName}",
            state = state.copy(program = updated),
        )
    }

    private fun removeFunctionParameter(
        state: K2IncrementalFuzzState,
        unusedLogicalSources: Map<String, String>,
        unusedOutputClassFiles: Set<String>,
        unusedStepIndex: Int,
    ): K2IncrementalStateMutation? {
        val updated = state.program.deepCopy()
        val classes = updated.classes
        val overriddenFunctions = classes.flatMap { it.functions }.flatMap { function ->
            function.override.map { it.containingClassName to it.name }
        }.toSet()
        val candidates = classes.flatMap { clazz ->
            if (clazz.language != Language.KOTLIN) return@flatMap emptyList()
            clazz.functions.flatMap { function ->
                if (function.isOverride || function.isOverrideStub ||
                    (clazz.name to function.name) in overriddenFunctions
                ) {
                    emptyList()
                } else {
                    function.parameterList.parameters.indices.map { parameterIndex ->
                        ParameterRemovalTarget(clazz, function, parameterIndex)
                    }
                }
            }
        }
        val target = candidates.randomOrNull(random) ?: return null
        val removed = target.function.parameterList.parameters.removeAt(target.parameterIndex)
        return K2IncrementalStateMutation(
            operator = "REMOVE_FUNCTION_PARAMETER",
            description = "${target.clazz.name}.${target.function.name}.parameter[${target.parameterIndex}]=${removed.name}",
            state = state.copy(program = updated),
        )
    }

    private fun removeGenericArgument(
        state: K2IncrementalFuzzState,
        unusedLogicalSources: Map<String, String>,
        unusedOutputClassFiles: Set<String>,
        unusedStepIndex: Int,
    ): K2IncrementalStateMutation? {
        val updated = state.program.deepCopy()
        val candidates = mutableListOf<GenericArgumentRemovalTarget>()
        updated.classes.forEach { clazz ->
            val classPrefix = "class ${clazz.name}"
            clazz.superType?.let { collectGenericArguments(it, "$classPrefix.superType", candidates) }
            clazz.implementedTypes.forEachIndexed { index, type ->
                collectGenericArguments(type, "$classPrefix.interface[$index]", candidates)
            }
            clazz.typeParameters.forEach { parameter ->
                collectGenericArguments(parameter.upperbound, "$classPrefix.typeParameter.${parameter.name}.bound", candidates)
            }
            clazz.functions.forEachIndexed { functionIndex, function ->
                val functionPrefix = "$classPrefix.function[$functionIndex].${function.name}"
                collectGenericArguments(function.returnType, "$functionPrefix.returnType", candidates)
                function.parameterList.parameters.forEachIndexed { parameterIndex, parameter ->
                    collectGenericArguments(parameter.type, "$functionPrefix.parameter[$parameterIndex]", candidates)
                }
                function.typeParameters.forEach { parameter ->
                    collectGenericArguments(parameter.upperbound, "$functionPrefix.typeParameter.${parameter.name}.bound", candidates)
                }
            }
        }
        val target = candidates.randomOrNull(random) ?: return null
        val oldArgument = target.classifier.arguments.getValue(target.parameterName)
        target.classifier.arguments[target.parameterName] = oldArgument.first to IrTypeArgument.STAR
        return K2IncrementalStateMutation(
            operator = "REMOVE_GENERIC_ARGUMENT",
            description = "${target.location}<${target.parameterName.value}>:${oldArgument.second.variance}->STAR",
            state = state.copy(program = updated),
        )
    }

    private fun collectGenericArguments(
        type: IrType,
        location: String,
        candidates: MutableList<GenericArgumentRemovalTarget>,
    ) {
        when (type) {
            is IrParameterizedClassifier -> {
                type.arguments.entries.sortedBy { it.key.value }.forEach { (parameterName, parameterAndArgument) ->
                    val argument = parameterAndArgument.second
                    if (!argument.isStar) {
                        candidates += GenericArgumentRemovalTarget(type, parameterName, location)
                    }
                    argument.type?.let { nested ->
                        collectGenericArguments(nested, "$location<${parameterName.value}>", candidates)
                    }
                }
            }
            is IrTypeContainer -> collectGenericArguments(type.innerType, "$location.inner", candidates)
        }
    }

    private fun removeUnreferencedClass(
        state: K2IncrementalFuzzState,
        unusedLogicalSources: Map<String, String>,
        unusedOutputClassFiles: Set<String>,
        unusedStepIndex: Int,
    ): K2IncrementalStateMutation? {
        val kotlinClasses = state.program.classes.filter { it.language == Language.KOTLIN }
        if (kotlinClasses.size <= 1) return null
        val referencedClasses = state.program.classDependencyGraph.values.flatten().toSet()
        val target = kotlinClasses.filter { it !in referencedClasses }.randomOrNull(random) ?: return null
        val updated = state.program.deepCopy()
        updated.modules.single().classes.removeAll { it.name == target.name }
        return K2IncrementalStateMutation(
            operator = "REMOVE_UNREFERENCED_CLASS",
            description = target.name,
            state = state.copy(program = updated),
        )
    }

    private fun moveKotlinSourceFile(
        state: K2IncrementalFuzzState,
        logicalSources: Map<String, String>,
        unusedOutputClassFiles: Set<String>,
        stepIndex: Int,
    ): K2IncrementalStateMutation? {
        val logicalPath = logicalSources.keys.filter { it.endsWith(".kt") }.randomOrNull(random) ?: return null
        val oldPath = state.sourceLayout[logicalPath] ?: logicalPath
        val fileName = Path.of(oldPath).fileName.toString()
        val newPath = "moved/${stepIndex.toString().padStart(4, '0')}/$fileName"
        if (oldPath == newPath || state.sourceLayout.values.contains(newPath)) return null
        return K2IncrementalStateMutation(
            operator = "MOVE_SOURCE_FILE",
            description = "$oldPath->$newPath",
            state = state.copy(sourceLayout = state.sourceLayout + (logicalPath to newPath)),
        )
    }

    private fun deleteKotlinOutputClass(
        state: K2IncrementalFuzzState,
        unusedLogicalSources: Map<String, String>,
        incrementalOutputClassFiles: Set<String>,
        unusedStepIndex: Int,
    ): K2IncrementalStateMutation? {
        val classNames = state.program.classes.map { it.name }.toSet()
        val target = incrementalOutputClassFiles
            .filter { it.startsWith("kotlin/") && Path.of(it).fileName.toString().removeSuffix(".class") in classNames }
            .sorted()
            .randomOrNull(random)
            ?: return null
        return K2IncrementalStateMutation(
            operator = "DELETE_INCREMENTAL_OUTPUT_CLASS",
            description = target,
            state = state,
            outputAction = K2IncrementalOutputAction(target),
        )
    }

    private data class ParameterTarget(
        val className: String,
        val functionIndex: Int,
        val parameterIndex: Int,
        val oldTypeName: String?,
    )

    private data class InterfaceFunctionTarget(
        val className: String,
        val functionIndex: Int,
        val functionName: String,
        val wasAbstract: Boolean,
    )

    private data class FunctionTarget(
        val className: String,
        val functionIndex: Int,
        val functionName: String,
    )

    private data class ParameterRemovalTarget(
        val clazz: IrClassDeclaration,
        val function: IrFunctionDeclaration,
        val parameterIndex: Int,
    )

    private data class GenericArgumentRemovalTarget(
        val classifier: IrParameterizedClassifier,
        val parameterName: IrTypeParameterName,
        val location: String,
    )
}

internal fun applySourceLayout(
    logicalSources: Map<String, String>,
    sourceLayout: Map<String, String>,
): Map<String, String> {
    val mapped = logicalSources.map { (logicalPath, content) ->
        (sourceLayout[logicalPath] ?: logicalPath) to content
    }.toMap()
    require(mapped.size == logicalSources.size) { "Source layout maps multiple logical files to one path" }
    return mapped
}
