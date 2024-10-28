package com.github.xyzboom.codesmith.mutator

import kotlin.reflect.full.memberProperties
import kotlin.reflect.typeOf

data class MutatorConfig(
    /**
     * ```kt
     * internal open class A
     * class B: A()
     * //       ^^^
     * // 'public' subclass exposes its 'internal' supertype A
     * ```
     */
    val ktExposeKtInternal: Boolean = true,
    // As there is no compilation error for this, false default.
    val javaExposeKtInternal: Boolean = false,
    /**
     * ```kt
     * open class A {
     *     private constructor()
     * }
     * class B: A {
     *     constructor(): super()
     *     //             ^^^^^^^
     *     // Cannot access '<init>': it is private in 'A'
     * }
     * ```
     */
    val constructorSuperCallPrivate: Boolean = false,
    /**
     * ```kt
     * // MODULE: a
     * open class A {
     *     internal constructor()
     * }
     * // MODULE: b(a)
     * class B: A {
     *     constructor(): super()
     *     //             ^^^^^^^
     *     // Cannot access '<init>': it is internal in 'A'
     * }
     * ```
     */
    val constructorSuperCallInternal: Boolean = false,
    /**
     * ```kt
     * open class A {
     *     private constructor()
     * }
     *
     * open class B {
     *     constructor(a: A)
     * }
     *
     * class C: B {
     *     constructor(): super(A())
     *     //                   ^
     *     // [INVISIBLE_MEMBER] Cannot access '<init>': it is private in 'A'
     * }
     * ```
     */
    val constructorNormalCallPrivate: Boolean = false,
    /**
     * ```kt
     * // MODULE: a
     * open class A {
     *     internal constructor()
     * }
     * // MODULE: b(a)
     * open class B {
     *     constructor(a: A)
     * }
     *
     * class C: B {
     *     constructor(): super(A())
     *     //                   ^
     *     // [INVISIBLE_MEMBER] Cannot access '<init>': it is internal in 'A'
     * }
     * ```
     */
    val constructorNormalCallInternal: Boolean = false,
    /**
     * ```kt
     * class A {
     *     private fun func() {
     *     }
     * }
     *
     * class B {
     *     fun func() {
     *         A().func()
     *         //  ^^^^
     *         // [INVISIBLE_MEMBER] Cannot access 'func': it is private in 'A'
     *     }
     * }
     * ```
     */
    val functionCallPrivate: Boolean = false,
    val functionCallInternal: Boolean = false,
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