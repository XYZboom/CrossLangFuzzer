package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.generator.IrTypeMatcher
import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildPlatformType
import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertInstanceOf
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

    @Test
    fun testGetActualTypeFromArgumentsReplacesTypeParameterInsidePlatformType() {
        val t0 = buildTypeParameter { name = "T0"; upperbound = IrAny }
        val platformT0 = buildPlatformType { innerType = t0 }
        val typeArguments = mapOf(IrTypeParameterName(t0.name) to (t0 to IrAny))

        getActualTypeFromArguments(platformT0, typeArguments, true) shouldBe IrTypeMatcher(IrAny)
    }

    @Test
    fun testGetActualTypeFromArgumentsReplacesParameterizedTypeInsidePlatformType() {
        val t0 = buildTypeParameter { name = "T0"; upperbound = IrAny }
        val wrapper = buildClassDeclaration {
            name = "Wrapper"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            typeParameters += t0
        }
        val wrapperOfT0 = wrapper.type as IrParameterizedClassifier
        wrapperOfT0.putTypeArgument(t0, t0)
        val platformWrapperOfT0 = buildPlatformType { innerType = wrapperOfT0 }
        val typeArguments = mapOf(IrTypeParameterName(t0.name) to (t0 to IrAny))
        val wrapperOfAny = wrapper.type as IrParameterizedClassifier
        wrapperOfAny.putTypeArgument(t0, IrAny)
        val actual = getActualTypeFromArguments(platformWrapperOfT0, typeArguments, true)

        assertInstanceOf(IrPlatformType::class.java, actual)
        (actual as IrPlatformType).innerType shouldBe IrTypeMatcher(wrapperOfAny)
    }

    // todo we need more test here, see comments in IrTypeReplacer
}