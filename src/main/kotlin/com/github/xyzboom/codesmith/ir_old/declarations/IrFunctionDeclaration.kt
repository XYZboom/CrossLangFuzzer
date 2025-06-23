package com.github.xyzboom.codesmith.ir_old.declarations

import com.fasterxml.jackson.annotation.*
import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.ir_old.IrParameterList
import com.github.xyzboom.codesmith.ir_old.IrProgram
import com.github.xyzboom.codesmith.ir_old.container.IrContainer
import com.github.xyzboom.codesmith.ir_old.container.IrTypeParameterContainer
import com.github.xyzboom.codesmith.ir_old.expressions.IrBlock
import com.github.xyzboom.codesmith.ir_old.types.IrType
import com.github.xyzboom.codesmith.ir_old.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir_old.types.builtin.IrUnit
import com.github.xyzboom.codesmith.ir_old.visitor.IrVisitor
import io.github.oshai.kotlinlogging.KotlinLogging

@JsonTypeName("function")
class IrFunctionDeclaration(
    name: String,
    @JsonIdentityReference
    var container: IrContainer
) : IrDeclaration(name), IrClassMember, IrTypeParameterContainer {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

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
//            if (parameterTypes != other.parameterTypes) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
//            result = 31 * result + parameterTypes.hashCode()
            return result
        }
    }

    fun StringBuilder.traceMe() {
        append(this@IrFunctionDeclaration.toString())
        append(" from ")
        val container = container
        if (container is IrClassDeclaration) {
            append("class ")
            append(container.name)
        }
    }

    fun traverseOverride(
        visitor: (IrFunctionDeclaration) -> Unit
    ) {
        logger.trace {
            val sb = StringBuilder("start traverse: ")
            sb.traceMe()
            sb.toString()
        }
        override.forEach {
            visitor(it)
            it.traverseOverride(visitor)
        }
        logger.trace {
            val sb = StringBuilder("end traverse: ")
            sb.traceMe()
            sb.toString()
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