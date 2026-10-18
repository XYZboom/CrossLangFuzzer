package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.render

/**
 * Represents [IrFunctionDeclaration]'s signature.
 * [compareParameterTypes] keeps the generator's historical name-only grouping by
 * default, while semantic validation can opt into exact source-level signatures.
 */
class Signature(
    val name: String,
    val parameterTypes: List<IrType>,
    val compareParameterTypes: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Signature) return false
        if (name != other.name) return false
        if (compareParameterTypes != other.compareParameterTypes) return false
        if (!compareParameterTypes) return true
        if (parameterTypes.size != other.parameterTypes.size) return false
        return parameterTypes.indices.all { index ->
            parameterTypes[index].render() == other.parameterTypes[index].render()
        }
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + compareParameterTypes.hashCode()
        if (compareParameterTypes) {
            result = 31 * result + parameterTypes.map { it.render() }.hashCode()
        }
        return result
    }
}