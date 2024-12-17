package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.expressions.IrExpression
import com.github.xyzboom.codesmith.ir.types.IrType

class IrParameter(
    name: String,
    val type: IrType
) : IrDeclaration(name) {
    var defaultValue: IrExpression? = null

}