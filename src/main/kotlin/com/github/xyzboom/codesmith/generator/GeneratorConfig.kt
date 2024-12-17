package com.github.xyzboom.codesmith.generator

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
) {
    companion object {
        @JvmStatic
        val default = GeneratorConfig()
    }
}