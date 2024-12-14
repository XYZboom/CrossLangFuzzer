package com.github.xyzboom.codesmith.ir.expressions.constant

import com.github.xyzboom.codesmith.ir.expressions.IrExpression

abstract class IrConstant<T>: IrExpression() {
    abstract val value: T
}