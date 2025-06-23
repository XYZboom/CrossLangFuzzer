package com.github.xyzboom.codesmith.ir_old.container

import com.github.xyzboom.codesmith.ir_old.types.IrTypeParameter

interface IrTypeParameterContainer {
    val typeParameters: MutableList<IrTypeParameter>
}