package com.github.xyzboom.codesmith.generator

import org.jetbrains.annotations.TestOnly

data class GeneratorConfig(
    val nameLengthRange: IntRange = 3..8,
    /**
     * Probability that a class or interface has a super class or interface
     */
    val classHasSuperProbability: Float = 0.4f,
    val javaRatio: Float = 0.5f,
    val classNumRange: IntRange = 5..9,
    val classImplNumRange: IntRange = 0..3,
    val functionNumRange: IntRange = 1..3,
    val functionParameterNumRange: IntRange = 0..3,
    val functionExpressionNumRange: IntRange = 2..8,
    /**
     * If true, override functions will only be generated for situations that must override.
     * Such as: there are unimplemented functions in super types;
     * there are conflict functions in super types;
     */
    val overrideOnlyMustOnes: Boolean = false,
    val noFinalFunction: Boolean = false
) {
    companion object {
        @JvmStatic
        val default = GeneratorConfig()

        @JvmStatic
        @get:TestOnly
        val testDefault = GeneratorConfig(
            overrideOnlyMustOnes = true,
            noFinalFunction = true
        )
    }
}