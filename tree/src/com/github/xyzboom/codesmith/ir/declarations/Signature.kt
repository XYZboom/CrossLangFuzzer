package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.types.IrType

/**
 * Represents [IrFunctionDeclaration]'s signature.
 * To simplify comparison, when generating functions, each unrelated function name is different
 */
class Signature(
    val name: String,
    val parameterTypes: List<IrType>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Signature) return false
        if (name != other.name) return false
        return true
    }

    override fun hashCode(): Int {
        val result = name.hashCode()
        return result
    }
}