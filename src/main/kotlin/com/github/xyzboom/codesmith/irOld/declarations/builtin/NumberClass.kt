package com.github.xyzboom.codesmith.irOld.declarations.builtin

import com.github.xyzboom.codesmith.irOld.declarations.IrClass
import com.github.xyzboom.codesmith.irOld.expressions.IrConstantExpression
import com.github.xyzboom.codesmith.irOld.expressions.IrExpression
import com.github.xyzboom.codesmith.irOld.types.IrClassType
import com.github.xyzboom.codesmith.irOld.types.IrConcreteType
import com.github.xyzboom.codesmith.irOld.types.builtin.IrBuiltinTypes
import kotlin.random.Random

object NumberClass: AbstractBuiltinClass("Number", IrBuiltinTypes.ANY, IrClassType.OPEN) {
    override val type: IrConcreteType get() = IrBuiltinTypes.NUMBER
    override fun generateValueArgumentFor(random: Random, clazz: IrClass): IrExpression {
        return IrConstantExpression.Number(random.nextInt())
    }
}