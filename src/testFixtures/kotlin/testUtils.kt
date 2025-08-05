package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.areEqualTypes
import com.github.xyzboom.codesmith.ir.types.render
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
        assertTrue(areEqualTypes(expectedType, param.type), "type of parameter $index in $name is unexpected!\n" +
                "expect: ${expectedType.render()}, actual: ${param.type.render()}")
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
            assertEquals(func.name, name)
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