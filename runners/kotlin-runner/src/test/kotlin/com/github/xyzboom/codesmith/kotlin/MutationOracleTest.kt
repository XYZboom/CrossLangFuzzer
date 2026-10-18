package com.github.xyzboom.codesmith.kotlin

import com.github.xyzboom.codesmith.mutator.SemanticValidity
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MutationOracleTest {
    @Test
    fun `valid mutation expects compiler acceptance`() {
        assertTrue(isMutationOracleSatisfied(SemanticValidity.VALID, compileSuccess = true))
        assertFalse(isMutationOracleSatisfied(SemanticValidity.VALID, compileSuccess = false))
    }

    @Test
    fun `invalid mutation expects compiler rejection`() {
        assertTrue(isMutationOracleSatisfied(SemanticValidity.INVALID, compileSuccess = false))
        assertFalse(isMutationOracleSatisfied(SemanticValidity.INVALID, compileSuccess = true))
    }

    @Test
    fun `unknown mutation must be discarded before compilation`() {
        assertThrows<IllegalStateException> {
            isMutationOracleSatisfied(SemanticValidity.UNKNOWN, compileSuccess = true)
        }
    }
}
