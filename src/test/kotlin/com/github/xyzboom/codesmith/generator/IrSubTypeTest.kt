package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.com.github.xyzboom.codesmith.generator.IrSubTypeMatcher
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class IrSubTypeTest {
    private companion object {
        @JvmStatic
        fun provideIRTypes(): List<Arguments> {
            val result = mutableListOf<Arguments>()
            return result.apply {
                val generator0 = IrDeclGenerator()
                val args0 = Arguments.of(
                    generator0,
                    buildTypeParameter { name = "T0"; upperbound = buildNullableType { innerType = IrAny } },
                    buildTypeParameter { name = "T1"; upperbound = IrAny },
                    false
                )
                result.add(args0)
                val args1 = Arguments.of(
                    generator0,
                    buildTypeParameter { name = "T0"; upperbound = IrAny },
                    buildTypeParameter { name = "T1"; upperbound = buildNullableType { innerType = IrAny } },
                    false
                )
                result.add(args1)
                val args2 = Arguments.of(
                    generator0,
                    IrAny,
                    buildTypeParameter { name = "T0"; upperbound = buildNullableType { innerType = IrAny } },
                    false
                )
                result.add(args2)
                val args3 = Arguments.of(
                    generator0,
                    IrAny,
                    buildTypeParameter { name = "T0"; upperbound = IrAny },
                    true
                )
                result.add(args3)
                run {
                    val t0 = buildTypeParameter { name = "T0"; upperbound = buildNullableType { innerType = IrAny } }
                    val t1 = buildTypeParameter { name = "T1"; upperbound = t0 }
                    val t2 = buildTypeParameter { name = "T2"; upperbound = IrAny }
                    val t3 = buildTypeParameter { name = "T3"; upperbound = t2 }
                    val args4 = Arguments.of(generator0, t3, t1, false)
                    result.add(args4)
                }
                run {
                    val t0 = buildTypeParameter { name = "T0"; upperbound = buildNullableType { innerType = IrAny } }
                    val t1 = buildTypeParameter { name = "T1"; upperbound = t0 }
                    val args4 = Arguments.of(generator0, IrAny, t1, false)
                    result.add(args4)
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("provideIRTypes")
    fun testSubType(generator: IrDeclGenerator, superType: IrType, subType: IrType, expected: Boolean) {
        if (expected) {
            subType shouldBe IrSubTypeMatcher(generator, superType)
        } else {
            subType shouldNotBe IrSubTypeMatcher(generator, superType)
        }
    }

}