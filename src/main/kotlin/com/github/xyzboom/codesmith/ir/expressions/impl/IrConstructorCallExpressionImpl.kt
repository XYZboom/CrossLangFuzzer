package com.github.xyzboom.codesmith.ir.expressions.impl

import com.github.xyzboom.codesmith.ir.declarations.IrConstructor
import com.github.xyzboom.codesmith.ir.expressions.IrConstructorCallExpression
import com.github.xyzboom.codesmith.ir.expressions.IrExpression

class IrConstructorCallExpressionImpl(
    override val callTarget: IrConstructor,
    override val valueArguments: List<IrExpression>
): IrConstructorCallExpression {
}