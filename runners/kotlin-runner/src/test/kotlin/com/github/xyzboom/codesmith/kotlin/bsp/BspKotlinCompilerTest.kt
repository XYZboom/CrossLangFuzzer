package com.github.xyzboom.codesmith.kotlin.bsp

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BspKotlinCompilerTest {
    companion object {
        @JvmStatic
        @AfterAll
        fun tearDown() {
            BspServerManager.close()
        }
    }

    @Test
    fun testCompileValidCodeViaBsp() {
        val code = """
            fun main() {
                println("Hello from BSP!")
            }
        """.trimIndent()
        val result = BspKotlinCompiler("2.0.21").compileSource(code)
        assertNull(result.majorResult, "Expected compile to succeed, but got: ${result.majorResult}")
    }

    @Test
    fun testCompileInvalidCodeViaBsp() {
        val code = """
            fun main() {
                unresolvedSymbol()
            }
        """.trimIndent()
        val result = BspKotlinCompiler("2.0.21").compileSource(code)
        assertTrue(result.majorResult != null, "Expected compile failure on unresolved symbol")
        assertTrue(result.majorResult!!.contains("unresolved reference"))
    }
}
