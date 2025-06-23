package com.github.xyzboom.codesmith.ir_old.expressions.constant

import com.github.xyzboom.codesmith.ir_old.expressions.IrExpression

abstract class IrConstant<T>: IrExpression() {
    abstract val value: T
}