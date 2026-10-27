package com.github.xyzboom.codesmith.ir

import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.IrDeclGenerator
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.types.builder.buildSimpleClassifier
import kotlin.random.Random

internal data class GeneratedSccTestProgram(val seed: Int, val program: IrProgram)

/** Generate test IR with a real parameterized override; do not hand-build declarations. */
internal fun generatedSccTestProgram(): GeneratedSccTestProgram {
    val config = GeneratorConfig(
        javaRatio = 0.0f,
        topLevelDeclRange = 5..5,
        classImplNumRange = 1..2,
        classMemberNumRange = 1..2,
        classMemberIsFunctionWeight = 1,
        classMemberIsPropertyWeight = 0,
        functionParameterNumRange = 1..2,
        functionExpressionNumRange = 0..0,
        classHasTypeParameterProbability = 0.0f,
        functionHasTypeParameterProbability = 0.0f,
        enableUseSiteVariance = false,
        overrideOnlyMustOnes = true,
        noFinalFunction = true,
        noFinalProperties = true,
        moduleNumRange = 1..1,
    )
    for (seed in 0..255) {
        val program = IrDeclGenerator(config, Random(seed), Language.KOTLIN).genProgram()
        val hasParameterizedOverride = program.classes.any { clazz ->
            clazz.functions.any { function ->
                function.isOverride && !function.isOverrideStub &&
                    function.parameterList.parameters.size >= 2 &&
                    function.override.any { overridden ->
                        function.parameterList.parameters.map { it.type } ==
                            overridden.parameterList.parameters.map { it.type }
                    }
            }
        }
        if (hasParameterizedOverride) return GeneratedSccTestProgram(seed, program)
    }
    error("Could not generate a single-module IR program with a parameterized override in seeds 0..255")
}

/** Create a multi-class SCC by changing existing generated IR parameter types. */
internal fun createGeneratedMultiClassScc(program: IrProgram): Set<String> {
    val functions = program.classes.mapNotNull { clazz ->
        val function = clazz.functions.firstOrNull {
            !it.isOverride && !it.isOverrideStub && it.parameterList.parameters.isNotEmpty()
        }
        function?.let { clazz to it }
    }
    require(functions.size >= 2) { "Generated IR needs two classes with parameterized non-override functions" }
    val (firstClass, firstFunction) = functions[0]
    val (secondClass, secondFunction) = functions[1]
    firstFunction.parameterList.parameters[0].type = buildSimpleClassifier { classDecl = secondClass }
    secondFunction.parameterList.parameters[0].type = buildSimpleClassifier { classDecl = firstClass }
    program.classes.forEach { clazz ->
        clazz.functions.filter { it.isOverride && !it.isOverrideStub }.forEach { function ->
            val overridden = function.override.firstOrNull() ?: return@forEach
            if (function.parameterList.parameters.size == overridden.parameterList.parameters.size) {
                function.parameterList.parameters.zip(overridden.parameterList.parameters).forEach { (parameter, baseParameter) ->
                    parameter.type = baseParameter.type
                }
            }
        }
    }
    val component = program.topologicallyOrderedClassComponents.single { group ->
        group.any { it.name == firstClass.name } && group.any { it.name == secondClass.name }
    }
    return component.map { it.name }.toSet()
}
