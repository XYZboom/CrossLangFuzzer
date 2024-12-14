package com.github.xyzboom.codesmith.irOld.types

interface IrTypeParameter: IrTypeArgument {
    val upperBound: IrConcreteType
    val name: String
}