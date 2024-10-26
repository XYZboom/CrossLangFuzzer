package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.IrAccessModifier
import com.github.xyzboom.codesmith.ir.expressions.IrConstructorCallExpression
import com.github.xyzboom.codesmith.ir.expressions.IrExpression

interface IrSpecialConstructor: IrConstructor {
    override var accessModifier: IrAccessModifier
    override val superCall: IrConstructorCallExpression
        get() = throw UnsupportedOperationException("superCall is not supported for this class")
    override val valueParameters: MutableList<IrValueParameter> get() = mutableListOf()
    override val expressions: MutableList<IrExpression> get() = mutableListOf()
    override val functions: MutableList<IrFunction> get() = mutableListOf()
}