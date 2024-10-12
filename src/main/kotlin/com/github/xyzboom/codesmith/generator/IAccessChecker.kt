package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.ir.declarations.*

interface IAccessChecker {
    /**
     * note that inside [file#fileCtx], current file is not added into module.
     */
    @IrGeneratorDsl
    val IrFile.accessibleClasses: Set<IrClass>

    @IrGeneratorDsl
    val IrPackage.accessibleClasses: Set<IrClass>

    @IrGeneratorDsl
    val IrModule.accessibleClasses: Set<IrClass>

    @IrGeneratorDsl
    val IrProgram.accessibleClasses: Set<IrClass>

    @IrGeneratorDsl
    fun IrFile.isAccessible(declaration: IrDeclaration): Boolean {
        return when (declaration) {
            is IrClass -> isAccessible(declaration)
            is IrFunction -> isAccessible(declaration)
        }
    }

    @IrGeneratorDsl
    fun IrFile.isAccessible(clazz: IrClass): Boolean

    @IrGeneratorDsl
    fun IrFile.isAccessible(function: IrFunction): Boolean

    @IrGeneratorDsl
    fun IrClass.isAccessible(declaration: IrDeclaration): Boolean {
        return when (declaration) {
            is IrClass -> isAccessible(declaration)
            is IrFunction -> isAccessible(declaration)
        }
    }

    @IrGeneratorDsl
    fun IrClass.isAccessible(clazz: IrClass): Boolean

    @IrGeneratorDsl
    fun IrClass.isAccessible(function: IrFunction): Boolean

    @IrGeneratorDsl
    fun IrDeclaration.isInSamePackage(declaration: IrDeclaration): Boolean

    @IrGeneratorDsl
    fun IrDeclaration.isInSameModule(declaration: IrDeclaration): Boolean

    @IrGeneratorDsl
    val IrDeclaration.containingPackage: IrPackage
}