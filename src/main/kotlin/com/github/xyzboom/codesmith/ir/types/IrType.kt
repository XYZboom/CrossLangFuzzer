package com.github.xyzboom.codesmith.ir.types

interface IrType: IrTypeArgument {
    val name: String
    var nullability: Nullability
}