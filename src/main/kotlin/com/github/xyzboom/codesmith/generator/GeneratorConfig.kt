package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.utils.rouletteSelection
import org.jetbrains.annotations.TestOnly
import kotlin.random.Random

data class GeneratorConfig(
    val nameLengthRange: IntRange = 3..8,
    /**
     * Probability that a class or interface has a super class or interface
     */
    val classHasSuperProbability: Float = 0.3f,
    val javaRatio: Float = 0.5f,
    val topLevelDeclRange: IntRange = 8..15,
    val topLevelClassWeight: Int = 3,
    val topLevelFunctionWeight: Int = 1,
    val topLevelPropertyWeight: Int = 1,
    val classImplNumRange: IntRange = 0..3,
    val classMemberNumRange: IntRange = 1..5,
    val classMemberIsFunctionWeight: Int = 3,
    val classMemberIsPropertyWeight: Int = 2,
    val functionParameterNumRange: IntRange = 0..3,
    val functionExpressionNumRange: IntRange = 2..8,
    val functionParameterNullableProbability: Float = 0.4f,
    val functionReturnTypeNullableProbability: Float = 0.4f,
    val printJavaNullableAnnotationProbability: Float = 0.4f,
    val newExpressionWeight: Int = 1,
    val functionCallExpressionWeight: Int = 1,
    /**
     * If true, override functions will only be generated for situations that must override.
     * Such as: there are unimplemented functions in super types;
     * there are conflict functions in super types;
     */
    val overrideOnlyMustOnes: Boolean = false,
    val noFinalFunction: Boolean = false,
    val noFinalProperties: Boolean = false,
) {
    fun randomClassMemberGenerator(
        declGenerator: IrDeclGenerator,
        random: Random = Random.Default
    ): IrClassMemberGenerator {
        val generators = listOf(
            declGenerator::genFunction,
            declGenerator::genProperty
        )
        val weights = listOf(
            classMemberIsFunctionWeight,
            classMemberIsPropertyWeight,
        )
        return rouletteSelection(generators, weights, random)
    }

    fun randomTopLevelDeclGenerator(
        declGenerator: IrDeclGenerator,
        random: Random = Random.Default
    ): IrTopLevelDeclGenerator {
        val generators = listOf(
            declGenerator::genTopLevelClass,
            declGenerator::genTopLevelFunction,
            declGenerator::genTopLevelProperty
        )
        val weights = listOf(
            topLevelClassWeight,
            topLevelFunctionWeight,
            topLevelPropertyWeight
        )
        return rouletteSelection(generators, weights, random)
    }

    fun randomExpressionGenerator(
        declGenerator: IrDeclGenerator,
        random: Random = Random.Default
    ): IrExpressionGenerator {
        val generators = listOf(
            declGenerator::genNewExpression,
            declGenerator::genFunctionCall
        )
        val weights = listOf(
            newExpressionWeight,
            functionCallExpressionWeight
        )
        return rouletteSelection(generators, weights, random)
    }

    companion object {
        @JvmStatic
        val default = GeneratorConfig()

        @JvmStatic
        @get:TestOnly
        val testDefault = GeneratorConfig(
            overrideOnlyMustOnes = true,
            noFinalFunction = true,
            noFinalProperties = true,
        )
    }
}