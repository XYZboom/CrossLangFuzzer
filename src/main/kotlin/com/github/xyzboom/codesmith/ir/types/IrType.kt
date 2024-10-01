package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.declarations.IrFile

sealed interface IrType: IrTypeArgument {
    val name: String
    val nullability: Nullability
    val classType: IrClassType
    val declaration: IrClass

    fun copy(nullability: Nullability): IrType

    val fullName: String
        get() = when(val typeContainer = declaration.containingDeclaration) {
            is IrClass -> TODO()
            is IrFile -> "${typeContainer.containingPackage.fullName}.$name"
        }
}