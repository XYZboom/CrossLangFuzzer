package com.github.xyzboom.codesmith.irOld.expressions

interface IrSpecialConstructorCallExpression: IrConstructorCallExpression {
    override val valueArguments: List<IrExpression>
        get() = emptyList()
}