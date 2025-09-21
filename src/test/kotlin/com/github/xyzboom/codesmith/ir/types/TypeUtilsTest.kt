package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test

private typealias TypeArgMap = Map<IrTypeParameterName, Pair<IrTypeParameter, IrType>>

class TypeUtilsTest {
    @Test
    fun testGetActualTypeFromArguments0() {
        val t0 = buildTypeParameter { name = "T0"; upperbound = IrAny }
        val t1 = buildTypeParameter { name = "T1"; upperbound = IrAny }
        val typeArgs: TypeArgMap = buildMap {
            set(t0, t1)
        }
        val getArg = getActualTypeFromArguments(t0, typeArgs, false)
        getArg shouldBeSameInstanceAs t1
    }

    @Test
    fun testGetActualTypeFromArguments1() {
        val t0 = buildTypeParameter { name = "T0"; upperbound = IrAny }
        val t1 = buildTypeParameter { name = "T1"; upperbound = IrAny }
        val typeArgs: TypeArgMap = buildMap {
            set(t0, t1)
        }
        val getArg = getActualTypeFromArguments(t0, typeArgs, false)
        getArg shouldBeSameInstanceAs t1
    }
}