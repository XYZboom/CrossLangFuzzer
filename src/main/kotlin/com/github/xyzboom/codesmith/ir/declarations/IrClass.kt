package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrConcreteType
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

interface IrClass: IrDeclaration, IrFunctionContainer, IrDeclarationContainer, IrClassContainer,
    IrAccessModifierContainer {
    val name: String
    val containingFile: IrFile
    val classType: IrClassType
    val type: IrType
    val superType: IrConcreteType?
    val implementedTypes: MutableList<IrConcreteType>
    val typeParameters: MutableList<IrTypeParameter>
    override val declarations: List<IrDeclaration>
        get() = ArrayList<IrDeclaration>(functions.size + classes.size).apply {
            addAll(functions)
            addAll(classes)
        }

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitClass(this, data)

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {
        declarations.forEach { it.accept(visitor, data) }
    }
}