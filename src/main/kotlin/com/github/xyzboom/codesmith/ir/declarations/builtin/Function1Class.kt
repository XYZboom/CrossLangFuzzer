package com.github.xyzboom.codesmith.ir.declarations.builtin

import com.github.xyzboom.codesmith.ir.declarations.IrValueParameter
import com.github.xyzboom.codesmith.ir.expressions.IrExpression
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrConcreteType
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltinTypes
import kotlin.random.Random

object Function1Class: AbstractBuiltinClass("Function1", IrBuiltinTypes.ANY, IrClassType.INTERFACE) {
    override val type: IrConcreteType get() = IrBuiltinTypes.FUNCTION1
    override fun generateValueArgumentFor(random: Random, valueParameter: IrValueParameter): IrExpression {
        TODO("Not yet implemented")
    }
}