package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.ir_old.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir_old.types.IrType
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun IrFunctionDeclaration.assertParameters(
    shouldBe: List<Pair<String, IrType>>
) {
    assertEquals(shouldBe.size, parameterList.parameters.size, "$name parameter size is unexpected!")
    for ((index, param) in parameterList.parameters.withIndex()) {
        val (expectedName, expectedType) = shouldBe[index]
        assertEquals(expectedName, param.name, "name of parameter $index in $name is unexpected!")
        assertEquals(expectedType, param.type, "name of parameter $index in $name is unexpected!")
    }
}

fun IrFunctionDeclaration.assertIsOverride(
    shouldFrom: List<IrFunctionDeclaration>,
    shouldBeSameSignature: Boolean,
    shouldHasBody: Boolean,
    shouldBeStub: Boolean,
    shouldBeFinal: Boolean = false
) {
    assertTrue(isOverride)
    if (shouldBeSameSignature) {
        for (func in shouldFrom) {
            assertTrue(signatureEquals(func))
        }
    }
    assertEquals(shouldFrom.size, override.size)
    assertContentEquals(shouldFrom.sortedBy { it.hashCode() }, override.sortedBy { it.hashCode() })
    assertEquals(
        shouldHasBody, body != null,
        "$name should ${if (shouldHasBody) "" else "not "}have a body"
    )
    assertEquals(
        shouldBeStub, isOverrideStub,
        "$name should ${if (shouldBeStub) "" else "not "}be a stub"
    )
    assertEquals(
        shouldBeFinal, isFinal,
        "$name should ${if (shouldBeFinal) "" else "not "}be final"
    )
}