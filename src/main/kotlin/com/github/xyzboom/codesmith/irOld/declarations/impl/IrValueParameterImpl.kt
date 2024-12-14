package com.github.xyzboom.codesmith.irOld.declarations.impl

import com.github.xyzboom.codesmith.irOld.declarations.IrValueParameter
import com.github.xyzboom.codesmith.irOld.types.IrType

class IrValueParameterImpl(
    override val name: String,
    override val type: IrType
): IrValueParameter {
}