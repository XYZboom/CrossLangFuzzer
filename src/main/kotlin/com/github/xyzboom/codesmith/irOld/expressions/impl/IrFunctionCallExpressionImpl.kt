package com.github.xyzboom.codesmith.irOld.expressions.impl

import com.github.xyzboom.codesmith.irOld.declarations.IrFunction
import com.github.xyzboom.codesmith.irOld.expressions.IrExpression
import com.github.xyzboom.codesmith.irOld.expressions.IrFunctionCallExpression

class IrFunctionCallExpressionImpl(
    override val receiver: IrExpression?,
    override val callTarget: IrFunction,
    override val valueArguments: List<IrExpression>
): IrFunctionCallExpression {

}