package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.container.IrFunctionContainer
import com.github.xyzboom.codesmith.ir.types.IrClassClassifier
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrSimpleClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

class IrClassDeclaration(
    name: String,
    var classType: IrClassType,
    val fields: MutableList<IrFieldDeclaration> = mutableListOf(),
    override val functions: MutableList<IrFunctionDeclaration> = mutableListOf()
): IrDeclaration(name), IrFunctionContainer {
    val superTypes = mutableListOf<IrType>()

    fun getType(): IrClassClassifier {
        return IrSimpleClassifier(this)
    }

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
        return visitor.visitClass(this, data)
    }

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {
        fields.forEach { it.accept(visitor, data) }
        functions.forEach { it.accept(visitor, data) }
    }
}