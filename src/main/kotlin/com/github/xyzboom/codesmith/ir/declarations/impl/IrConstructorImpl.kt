package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.IrAccessModifier
import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.declarations.IrConstructor
import com.github.xyzboom.codesmith.ir.declarations.IrFunction
import com.github.xyzboom.codesmith.ir.declarations.IrValueParameter
import com.github.xyzboom.codesmith.ir.expressions.IrConstructorCallExpression
import com.github.xyzboom.codesmith.ir.types.IrType

class IrConstructorImpl(
    override val accessModifier: IrAccessModifier,
    override val containingDeclaration: IrClass,
    override val superCall: IrConstructorCallExpression,
    override val valueParameters: MutableList<IrValueParameter> = mutableListOf(),
    override val functions: MutableList<IrFunction> = mutableListOf()
): IrConstructor {
    override val name: String = containingDeclaration.name
    override val returnType: IrType = containingDeclaration.type
}