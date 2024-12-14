package com.github.xyzboom.codesmith.irOld.declarations.impl

import com.github.xyzboom.codesmith.irOld.IrAccessModifier
import com.github.xyzboom.codesmith.irOld.declarations.IrClass
import com.github.xyzboom.codesmith.irOld.declarations.IrConstructor
import com.github.xyzboom.codesmith.irOld.declarations.IrFunction
import com.github.xyzboom.codesmith.irOld.declarations.IrValueParameter
import com.github.xyzboom.codesmith.irOld.expressions.IrConstructorCallExpression
import com.github.xyzboom.codesmith.irOld.expressions.IrExpression

class IrConstructorImpl(
    override var accessModifier: IrAccessModifier,
    override val containingDeclaration: IrClass,
    override val superCall: IrConstructorCallExpression,
    override val valueParameters: MutableList<IrValueParameter> = mutableListOf(),
    override val functions: MutableList<IrFunction> = mutableListOf(),
    override val expressions: MutableList<IrExpression> = mutableListOf(),
): IrConstructor