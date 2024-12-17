package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.container.IrFunctionContainer
import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

class IrFunctionDeclaration(
    name: String,
    var container: IrFunctionContainer
) : IrDeclaration(name) {
    var body: IrBlock? = null
    var isOverride: Boolean = false
    var isOverrideStub: Boolean = false
    var override = mutableListOf<IrFunctionDeclaration>()
    var isFinal = false
    val parameters = mutableListOf<IrParameter>()

    class Signature(
        val name: String,
        val parameterTypes: List<IrType>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Signature) return false

            if (name != other.name) return false
            if (parameterTypes != other.parameterTypes) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + parameterTypes.hashCode()
            return result
        }
    }

    val signature: Signature
        get() {
            return Signature(name, parameters.map { it.type })
        }

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
        return visitor.visitFunction(this, data)
    }

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {
        body?.accept(visitor, data)
    }

    fun signatureEquals(other: IrFunctionDeclaration): Boolean {
        return name == other.name && parameters.map { it.type } == other.parameters.map { it.type }
    }

    override fun toString(): String {
        return "${if (body == null) "abstract " else ""}fun $name"
    }
}