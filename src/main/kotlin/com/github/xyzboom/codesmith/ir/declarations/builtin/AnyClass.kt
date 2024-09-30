package com.github.xyzboom.codesmith.ir.declarations.builtin

import com.github.xyzboom.codesmith.ir.IrAccessModifier
import com.github.xyzboom.codesmith.ir.declarations.IrFunction
import com.github.xyzboom.codesmith.ir.declarations.impl.IrConstructorImpl
import com.github.xyzboom.codesmith.ir.declarations.impl.IrFunctionImpl
import com.github.xyzboom.codesmith.ir.declarations.impl.IrValueParameterImpl
import com.github.xyzboom.codesmith.ir.expressions.IrConstructorCallExpression.Companion.anyConstructor
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrConcreteType
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltinTypes

object AnyClass: AbstractBuiltinClass("Any", classType = IrClassType.OPEN) {
    override val functions: MutableList<IrFunction>
        get() = arrayListOf(
            IrFunctionImpl(
                "equals", this, IrAccessModifier.PUBLIC,
                mutableListOf(IrValueParameterImpl("other", type)), BooleanClass.type
            ),
            IrConstructorImpl(IrAccessModifier.PUBLIC, this, anyConstructor)
        )
    override val type: IrConcreteType get() = IrBuiltinTypes.ANY
}