package com.github.xyzboom.codesmith.ir_old.declarations

import com.github.xyzboom.codesmith.ir_old.expressions.IrExpression
import com.github.xyzboom.codesmith.ir_old.types.IrType

class IrParameter(
    name: String,
    var type: IrType
) : IrDeclaration(name) {
    fun copyForOverride(): IrParameter {
        return IrParameter(name, type.copy())
    }

    var defaultValue: IrExpression? = null

}