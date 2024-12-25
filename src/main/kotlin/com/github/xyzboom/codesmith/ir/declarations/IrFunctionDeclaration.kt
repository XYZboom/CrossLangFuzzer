package com.github.xyzboom.codesmith.ir.declarations

import com.fasterxml.jackson.annotation.*
import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.ir.IrParameterList
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.container.IrContainer
import com.github.xyzboom.codesmith.ir.container.IrTypeParameterContainer
import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrUnit
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

@JsonTypeName("function")
class IrFunctionDeclaration(
    name: String,
    @JsonIdentityReference
    var container: IrContainer
) : IrDeclaration(name), IrClassMember, IrTypeParameterContainer {
    /**
     * only available when [language] is [Language.JAVA]
     */
    var printNullableAnnotations: Boolean = false
    var body: IrBlock? = null
    var isOverride: Boolean = false
    var isOverrideStub: Boolean = false
    @JsonBackReference("override")
    var override = mutableListOf<IrFunctionDeclaration>()
    var isFinal = false
    @get:JsonIgnore
    val topLevel: Boolean get() = container is IrProgram
    var parameterList = IrParameterList()
    var returnType: IrType = IrUnit
    override val typeParameters: MutableList<IrTypeParameter> = mutableListOf()

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

    @get:JsonIgnore
    val signature: Signature
        get() {
            return Signature(name, parameterList.parameters.map { it.type })
        }

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
        return visitor.visitFunction(this, data)
    }

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {
        parameterList.accept(visitor, data)
        body?.accept(visitor, data)
    }

    fun signatureEquals(other: IrFunctionDeclaration): Boolean {
        return name == other.name &&
                parameterList.parameters.map { it.type } == other.parameterList.parameters.map { it.type }
    }

    override fun toString(): String {
        return "${if (body == null) "abstract " else ""}fun $name [" +
                "isOverride=$isOverride, isOverrideStub=$isOverrideStub, isFinal=$isFinal]"
    }
}