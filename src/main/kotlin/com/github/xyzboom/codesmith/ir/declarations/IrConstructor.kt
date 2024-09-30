package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.expressions.IrConstructorCallExpression

interface IrConstructor: IrFunction {
    val superCall: IrConstructorCallExpression
}