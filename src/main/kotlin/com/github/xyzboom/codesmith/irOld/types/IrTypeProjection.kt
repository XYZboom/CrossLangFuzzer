package com.github.xyzboom.codesmith.irOld.types

interface IrTypeProjection: IrTypeArgument {
    val variance: Variance
    val type: IrTypeArgument
}