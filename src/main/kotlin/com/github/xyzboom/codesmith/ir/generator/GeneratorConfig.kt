package com.github.xyzboom.codesmith.ir.generator

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
    /**
     * 每次从待依赖集合中剔除的比例
     */
    val moduleDependencyEliminateRate: Float = 0.3f
) {
    companion object {
        @JvmStatic
        val default = GeneratorConfig()
    }
}