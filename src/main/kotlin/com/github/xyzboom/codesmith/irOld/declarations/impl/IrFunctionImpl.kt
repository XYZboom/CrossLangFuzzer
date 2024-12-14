package com.github.xyzboom.codesmith.irOld.declarations.impl

import com.github.xyzboom.codesmith.irOld.IrAccessModifier
import com.github.xyzboom.codesmith.irOld.declarations.IrFunction
import com.github.xyzboom.codesmith.irOld.declarations.IrFunctionContainer
import com.github.xyzboom.codesmith.irOld.declarations.IrValueParameter
import com.github.xyzboom.codesmith.irOld.expressions.IrExpression
import com.github.xyzboom.codesmith.irOld.types.IrConcreteType

class IrFunctionImpl(
    override val name: String,
    override val containingDeclaration: IrFunctionContainer,
    override var accessModifier: IrAccessModifier = IrAccessModifier.PUBLIC,
    override val valueParameters: MutableList<IrValueParameter> = mutableListOf(),
    override val returnType: IrConcreteType,
    override val expressions: MutableList<IrExpression> = mutableListOf(),
): IrFunction {
    override val functions: MutableList<IrFunction> = ArrayList()

}