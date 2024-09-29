package com.github.xyzboom.codesmith.ir.types

interface IrTypeParameter: IrTypeArgument {
    val upperBound: IrConcreteType
    val name: String
}