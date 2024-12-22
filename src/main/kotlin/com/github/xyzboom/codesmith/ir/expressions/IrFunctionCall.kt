package com.github.xyzboom.codesmith.ir.expressions

import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration

class IrFunctionCall(
    val receiver: IrExpression?,
    val target: IrFunctionDeclaration,
    val arguments: List<IrValueArgument>
) : IrExpression() {
}