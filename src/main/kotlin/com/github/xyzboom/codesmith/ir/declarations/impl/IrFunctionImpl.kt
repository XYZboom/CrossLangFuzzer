package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.IrAccessModifier
import com.github.xyzboom.codesmith.ir.declarations.IrFunction
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionContainer
import com.github.xyzboom.codesmith.ir.declarations.IrValueParameter
import com.github.xyzboom.codesmith.ir.expressions.IrExpression
import com.github.xyzboom.codesmith.ir.types.IrConcreteType

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