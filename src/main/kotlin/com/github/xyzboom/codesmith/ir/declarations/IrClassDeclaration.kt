package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.container.IrContainer
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrClassifier
import com.github.xyzboom.codesmith.ir.types.IrSimpleClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

typealias SuperAndIntfFunctions = Pair<IrFunctionDeclaration?, MutableSet<IrFunctionDeclaration>>
//                                     ^^^^^^^^^^^^^^^^^^^^^ decl in super
//                                     functions in interfaces ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
typealias FunctionSignatureMap = Map<IrFunctionDeclaration.Signature, SuperAndIntfFunctions>


class IrClassDeclaration(
    name: String,
    var classType: IrClassType,
    val fields: MutableList<IrFieldDeclaration> = mutableListOf(),
    override val functions: MutableList<IrFunctionDeclaration> = mutableListOf()
) : IrDeclaration(name), IrContainer {
    var superType: IrType? = null
    val implementedTypes = mutableListOf<IrType>()
    val type: IrClassifier = IrSimpleClassifier(this)

    override val classes: MutableList<IrClassDeclaration> = mutableListOf()
    override var superContainer: IrContainer? = null

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
        return visitor.visitClass(this, data)
    }

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {
        fields.forEach { it.accept(visitor, data) }
        functions.forEach { it.accept(visitor, data) }
    }

    override fun toString(): String {
        return "class $name"
    }
}