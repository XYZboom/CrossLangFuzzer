package com.github.xyzboom.codesmith.ir.expressions.impl

import com.github.xyzboom.codesmith.ir.declarations.IrFunction
import com.github.xyzboom.codesmith.ir.expressions.IrExpression
import com.github.xyzboom.codesmith.ir.expressions.IrFunctionCallExpression

class IrFunctionCallExpressionImpl(
    override val receiver: IrExpression?,
    override val callTarget: IrFunction,
    override val valueArguments: List<IrExpression>
): IrFunctionCallExpression {

}