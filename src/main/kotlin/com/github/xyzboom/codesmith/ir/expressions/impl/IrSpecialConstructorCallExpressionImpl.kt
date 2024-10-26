package com.github.xyzboom.codesmith.ir.expressions.impl

import com.github.xyzboom.codesmith.ir.declarations.IrConstructor
import com.github.xyzboom.codesmith.ir.expressions.IrSpecialConstructorCallExpression

class IrSpecialConstructorCallExpressionImpl(override val callTarget: IrConstructor):
    IrSpecialConstructorCallExpression