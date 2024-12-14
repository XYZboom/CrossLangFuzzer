package com.github.xyzboom.codesmith.irOld.declarations.builtin

import com.github.xyzboom.codesmith.irOld.declarations.IrClass
import com.github.xyzboom.codesmith.irOld.expressions.IrConstantExpression
import com.github.xyzboom.codesmith.irOld.expressions.IrExpression
import com.github.xyzboom.codesmith.irOld.types.IrClassType
import com.github.xyzboom.codesmith.irOld.types.IrConcreteType
import com.github.xyzboom.codesmith.irOld.types.builtin.IrBuiltinTypes
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