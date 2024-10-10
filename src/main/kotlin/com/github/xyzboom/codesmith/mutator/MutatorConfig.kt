package com.github.xyzboom.codesmith.mutator

import kotlin.reflect.full.memberProperties
import kotlin.reflect.typeOf

data class MutatorConfig(
    val ktExposeKtInternal: Boolean = true,
    // As there is no compilation error for this, false default.
    val javaExposeKtInternal: Boolean = false,
) {
    fun anyEnabled(): Boolean {
        val properties = MutatorConfig::class.memberProperties
        return properties.map { it.getter }.filter { it.returnType == typeOf<Boolean>() }
            .any { it.call(this) as Boolean? == true }
    }

    companion object {
        @JvmStatic
        val default = MutatorConfig()
    }
}