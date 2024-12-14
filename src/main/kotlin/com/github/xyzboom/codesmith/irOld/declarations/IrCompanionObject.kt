package com.github.xyzboom.codesmith.irOld.declarations

import com.github.xyzboom.codesmith.irOld.IrAccessModifier
import com.github.xyzboom.codesmith.irOld.declarations.impl.IrSpecialConstructorImpl
import com.github.xyzboom.codesmith.irOld.types.IrClassType
import com.github.xyzboom.codesmith.irOld.visitor.IrVisitor

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