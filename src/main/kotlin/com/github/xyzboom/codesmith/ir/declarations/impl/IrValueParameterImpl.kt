package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.declarations.IrValueParameter
import com.github.xyzboom.codesmith.ir.types.IrType

class IrValueParameterImpl(
    override val name: String,
    override val type: IrType
): IrValueParameter {
}