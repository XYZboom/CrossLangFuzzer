package com.github.xyzboom.codesmith.ir.expressions

interface IrSpecialConstructorCallExpression: IrConstructorCallExpression {
    override val valueArguments: List<IrExpression>
        get() = emptyList()
}