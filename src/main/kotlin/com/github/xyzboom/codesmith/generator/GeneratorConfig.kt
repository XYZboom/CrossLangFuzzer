package com.github.xyzboom.codesmith.generator

data class GeneratorConfig(
    val moduleNumRange: IntRange = 1..8,
    val nameLengthRange: IntRange = 2..8,
    /**
     * 首次选取到待依赖集合元素的比例
     */
    val moduleDependencySelectionRate: Float = 0.4f,
    /**
     * 每次从待依赖集合中选取的最低比例
     */
    val moduleDependencyMinRate: Float = 0.3f,
    /**
     * 每次从待依赖集合中选取的最高比例
     */
    val moduleDependencyMaxRate: Float = 0.5f,
    val classHasSuperProbability: Float = 0.4f,
    /**
     * 每次从待依赖集合中剔除的比例
     */
    val moduleDependencyEliminateRate: Float = 0.3f,
    val packageNumRange: IntRange = 1..4,
    val fileNumRange: IntRange = 2..8,
    val classNumRange: IntRange = 1..3,
    val classImplNumRange: IntRange = 0..3,
    val constructorNumRange: IntRange = 1..3,
    val constructorParameterNumRange: IntRange = 0..4,
    val functionNumRange: IntRange = 1..3,
    val functionParameterNumRange: IntRange = 0..3,
    val functionExpressionNumRange: IntRange = 2..8,
) {
    companion object {
        @JvmStatic
        val default = GeneratorConfig()
    }
}