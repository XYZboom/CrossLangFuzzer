package com.github.xyzboom.codesmith.ir.types

interface IrTypeProjection: IrTypeArgument {
    val variance: Variance
    val type: IrTypeArgument
}