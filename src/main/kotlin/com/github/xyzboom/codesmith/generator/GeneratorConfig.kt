package com.github.xyzboom.codesmith.generator

data class GeneratorConfig(
    val classHasSuperProbability: Float = 0.4f,
    val javaRatio: Float = 0.5f,
    val classNumRange: IntRange = 1..3,
    val functionNumRange: IntRange = 1..3,
    val functionParameterNumRange: IntRange = 0..3,
    val functionExpressionNumRange: IntRange = 2..8,
) {
    companion object {
        @JvmStatic
        val default = GeneratorConfig()
    }
}