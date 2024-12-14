package com.github.xyzboom.codesmith.irOld.expressions.impl

import com.github.xyzboom.codesmith.irOld.declarations.IrConstructor
import com.github.xyzboom.codesmith.irOld.expressions.IrConstructorCallExpression
import com.github.xyzboom.codesmith.irOld.expressions.IrExpression

class IrConstructorCallExpressionImpl(
    override val callTarget: IrConstructor,
    override val valueArguments: List<IrExpression>
): IrConstructorCallExpression {
}