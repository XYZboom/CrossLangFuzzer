package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.utils.rouletteSelection
import org.jetbrains.annotations.TestOnly
import kotlin.random.Random

data class GeneratorConfig(
    val nameLengthRange: IntRange = 3..8,
    val javaRatio: Float = 0.5f,
    //<editor-fold desc="Top Level">
    val topLevelDeclRange: IntRange = 8..15,
    val topLevelClassWeight: Int = 3,
    val topLevelFunctionWeight: Int = 1,
    val topLevelPropertyWeight: Int = 1,
    //</editor-fold>
    //<editor-fold desc="Class">
    /**
     * Probability that a class or interface has a super class or interface
     */
    val classHasSuperProbability: Float = 0.3f,
    val classImplNumRange: IntRange = 0..3,
    val classMemberNumRange: IntRange = 1..3,
    val classMemberIsFunctionWeight: Int = 3,
    val classMemberIsPropertyWeight: Int = 2,
    val classHasTypeParameterProbability: Float = 0.3f,
    val classTypeParameterNumberRange: IntRange = 1..3,
    //</editor-fold>
    //<editor-fold desc="Function">
    val functionParameterNumRange: IntRange = 0..3,
    val functionExpressionNumRange: IntRange = 2..8,
    val functionParameterNullableProbability: Float = 0.4f,
    val functionReturnTypeNullableProbability: Float = 0.4f,
    //</editor-fold>
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