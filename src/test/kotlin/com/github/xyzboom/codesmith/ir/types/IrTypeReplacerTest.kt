package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.generator.IrTypeMatcher
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class IrTypeReplacerTest {

    @Test
    fun testReplaceType0() {
        val t0 = buildTypeParameter { name = "T0"; upperbound = IrAny }
        val nullableT0 = buildNullableType { innerType = t0 }
        val t1 = buildTypeParameter { name = "T1"; upperbound = IrAny }
        val nullableT1 = buildNullableType { innerType = t1 }
        nullableT0.replaceType(t0, t1) shouldBe IrTypeMatcher(nullableT1)
    }

    @Test
    fun testReplaceType1() {
        val t0 = buildTypeParameter { name = "T0"; upperbound = IrAny }
        val nullableT0 = buildNullableType { innerType = t0 }
        val t1 = buildTypeParameter { name = "T1"; upperbound = IrAny }
        val nullableT1 = buildNullableType { innerType = t1 }
        nullableT0.replaceType(t0, nullableT1) shouldBe IrTypeMatcher(nullableT1)
    }

    // todo we need more test here, see comments in IrTypeReplacer
}