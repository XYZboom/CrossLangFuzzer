package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.expressions.IrExpression
import com.github.xyzboom.codesmith.ir.types.IrType

class IrParameter(
    name: String,
    var type: IrType
) : IrDeclaration(name) {
    fun copyForOverride(): IrParameter {
        return IrParameter(name, type.copyForOverride())
    }

    var defaultValue: IrExpression? = null

}