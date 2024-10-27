package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.IrAccessModifier
import com.github.xyzboom.codesmith.ir.declarations.impl.IrSpecialConstructorImpl
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

abstract class IrCompanionObject: IrClass, IrDeclarationContainer {
    /**
     * Companion object can not have a companion object.
     */
    final override val companionObject: IrCompanionObject?
        get() = null
    abstract override val containingDeclaration: IrClass
    val containingClass: IrClass
        get() = containingDeclaration
    final override val classType: IrClassType = IrClassType.FINAL

    final override val name: String = "Companion"

    final override val specialConstructor: IrSpecialConstructor
        get() = IrSpecialConstructorImpl(this).apply {
            accessModifier = IrAccessModifier.PRIVATE
        }

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitCompanionObject(this, data)
}