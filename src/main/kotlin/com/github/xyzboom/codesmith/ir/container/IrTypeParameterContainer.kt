package com.github.xyzboom.codesmith.ir.container

import com.github.xyzboom.codesmith.ir.types.IrTypeParameter

interface IrTypeParameterContainer {
    val typeParameters: MutableList<IrTypeParameter>
}