package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.ir.declarations.IrClass

sealed interface IrType: IrTypeArgument {
    val name: String
    var nullability: Nullability
    val classType: IrClassType
    val declaration: IrClass
}