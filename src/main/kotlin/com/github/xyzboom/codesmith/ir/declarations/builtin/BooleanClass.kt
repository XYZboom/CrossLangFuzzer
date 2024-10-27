package com.github.xyzboom.codesmith.ir.declarations.builtin

import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.expressions.IrConstantExpression
import com.github.xyzboom.codesmith.ir.expressions.IrExpression
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrConcreteType
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltinTypes
import kotlin.random.Random

object BooleanClass: AbstractBuiltinClass("Boolean", IrBuiltinTypes.ANY, IrClassType.FINAL) {
    override val type: IrConcreteType get() = IrBuiltinTypes.BOOLEAN
    override fun generateValueArgumentFor(random: Random, clazz: IrClass): IrExpression {
        return if (random.nextBoolean()) {
            IrConstantExpression.True
        } else {
            IrConstantExpression.False
        }
    }
}