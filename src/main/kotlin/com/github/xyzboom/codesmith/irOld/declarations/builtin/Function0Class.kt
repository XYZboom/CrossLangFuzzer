package com.github.xyzboom.codesmith.irOld.declarations.builtin

import com.github.xyzboom.codesmith.irOld.declarations.IrClass
import com.github.xyzboom.codesmith.irOld.expressions.IrExpression
import com.github.xyzboom.codesmith.irOld.types.IrClassType
import com.github.xyzboom.codesmith.irOld.types.IrConcreteType
import com.github.xyzboom.codesmith.irOld.types.builtin.IrBuiltinTypes
import kotlin.random.Random

object Function0Class: AbstractBuiltinClass("Function0", IrBuiltinTypes.ANY, IrClassType.INTERFACE) {
    override val type: IrConcreteType get() = IrBuiltinTypes.FUNCTION0
    override fun generateValueArgumentFor(random: Random, clazz: IrClass): IrExpression {
        TODO("Not yet implemented")
    }
}