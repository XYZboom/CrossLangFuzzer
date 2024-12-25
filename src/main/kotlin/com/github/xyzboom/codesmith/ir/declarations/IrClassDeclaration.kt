package com.github.xyzboom.codesmith.ir.declarations

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonTypeName
import com.github.xyzboom.codesmith.ir.container.IrContainer
import com.github.xyzboom.codesmith.ir.container.IrTypeParameterContainer
import com.github.xyzboom.codesmith.ir.types.*
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

typealias SuperAndIntfFunctions = Pair<IrFunctionDeclaration?, MutableSet<IrFunctionDeclaration>>
//                                     ^^^^^^^^^^^^^^^^^^^^^ decl in super
//                                     functions in interfaces ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
typealias FunctionSignatureMap = Map<IrFunctionDeclaration.Signature, SuperAndIntfFunctions>

@JsonTypeName("class")
class IrClassDeclaration(
    name: String,
    var classType: IrClassType
) : IrDeclaration(name), IrContainer, IrTypeParameterContainer {
    var superType: IrType? = null
    val implementedTypes = mutableListOf<IrType>()
    val fields: MutableList<IrFieldDeclaration> = mutableListOf()
    override val functions: MutableList<IrFunctionDeclaration> = mutableListOf()
    override val properties: MutableList<IrPropertyDeclaration> = mutableListOf()

    @get:JsonIgnore
    val type: IrClassifier
        get() = if (typeParameters.isEmpty()) {
            IrSimpleClassifier(this)
        } else {
            IrParameterizedClassifier.create(this)
        }

    override val classes: MutableList<IrClassDeclaration> = mutableListOf()
    override var superContainer: IrContainer? = null
    override val typeParameters: MutableList<IrTypeParameter> = mutableListOf()
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
        return visitor.visitClass(this, data)
    }

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {
        fields.forEach { it.accept(visitor, data) }
        functions.forEach { it.accept(visitor, data) }
        properties.forEach { it.accept(visitor, data) }
    }

    override fun toString(): String {
        return "class $name"
    }
}