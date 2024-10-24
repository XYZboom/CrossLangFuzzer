package com.github.xyzboom.codesmith.checkers

import com.github.xyzboom.codesmith.CodeSmithDsl
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.declarations.*

interface IAccessChecker {
    /**
     * note that inside [file#fileCtx], current file is not added into module.
     */
    @CodeSmithDsl
    val IrFile.accessibleClasses: Set<IrClass>

    @CodeSmithDsl
    val IrPackage.accessibleClasses: Set<IrClass>

    @CodeSmithDsl
    val IrModule.accessibleClasses: Set<IrClass>

    @CodeSmithDsl
    val IrProgram.accessibleClasses: Set<IrClass>

    @CodeSmithDsl
    fun IrElement.isAccessible(declaration: IrDeclaration): Boolean {
        return when (this) {
            is IrClass -> isAccessible(declaration)
            is IrFile -> isAccessible(declaration)
            else -> throw IllegalStateException()
        }
    }

    @CodeSmithDsl
    fun IrFile.isAccessible(declaration: IrDeclaration): Boolean {
        return when (declaration) {
            is IrClass -> isAccessible(declaration)
            is IrFunction -> isAccessible(declaration)
        }
    }

    @CodeSmithDsl
    fun IrFile.isAccessible(clazz: IrClass): Boolean

    @CodeSmithDsl
    fun IrFile.isAccessible(function: IrFunction): Boolean

    @CodeSmithDsl
    fun IrClass.isAccessible(declaration: IrDeclaration): Boolean {
        return when (declaration) {
            is IrClass -> isAccessible(declaration)
            is IrFunction -> isAccessible(declaration)
        }
    }

    @CodeSmithDsl
    fun IrClass.isAccessible(clazz: IrClass): Boolean

    @CodeSmithDsl
    fun IrClass.isAccessible(function: IrFunction): Boolean

    @CodeSmithDsl
    fun IrDeclaration.isInSamePackage(declaration: IrDeclaration): Boolean

    @CodeSmithDsl
    fun IrDeclaration.isInSameModule(declaration: IrDeclaration): Boolean

    @CodeSmithDsl
    val IrDeclaration.containingPackage: IrPackage
}