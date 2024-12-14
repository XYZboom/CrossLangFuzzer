package com.github.xyzboom.codesmith.ir.expressions.constant

object IrNull: IrConstant<Nothing?>() {
    override val value: Nothing? = null
}