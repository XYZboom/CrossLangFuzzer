package com.github.xyzboom.codesmith.irOld.expressions.impl

import com.github.xyzboom.codesmith.irOld.declarations.IrConstructor
import com.github.xyzboom.codesmith.irOld.expressions.IrSpecialConstructorCallExpression

class IrSpecialConstructorCallExpressionImpl(override val callTarget: IrConstructor):
    IrSpecialConstructorCallExpression