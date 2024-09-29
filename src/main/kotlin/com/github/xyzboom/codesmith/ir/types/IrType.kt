package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.ir.declarations.IrClass

sealed interface IrType: IrTypeArgument {
    val name: String
    val nullability: Nullability
    val classType: IrClassType
    val declaration: IrClass

    fun copy(nullability: Nullability): IrType
}