package com.github.xyzboom.codesmith.irOld.declarations.builtin

import com.github.xyzboom.codesmith.irOld.IrAccessModifier
import com.github.xyzboom.codesmith.irOld.declarations.IrClass
import com.github.xyzboom.codesmith.irOld.declarations.IrFunction
import com.github.xyzboom.codesmith.irOld.declarations.impl.IrConstructorImpl
import com.github.xyzboom.codesmith.irOld.declarations.impl.IrFunctionImpl
import com.github.xyzboom.codesmith.irOld.declarations.impl.IrValueParameterImpl
import com.github.xyzboom.codesmith.irOld.expressions.IrConstructorCallExpression.Companion.anyConstructor
import com.github.xyzboom.codesmith.irOld.expressions.IrExpression
import com.github.xyzboom.codesmith.irOld.expressions.impl.IrConstructorCallExpressionImpl
import com.github.xyzboom.codesmith.irOld.types.IrClassType
import com.github.xyzboom.codesmith.irOld.types.IrConcreteType
import com.github.xyzboom.codesmith.irOld.types.builtin.IrBuiltinTypes
import kotlin.random.Random

object AnyClass: AbstractBuiltinClass("Any", classType = IrClassType.OPEN) {
    val constructor = IrConstructorImpl(IrAccessModifier.PUBLIC, this, anyConstructor)
    override val functions: MutableList<IrFunction>
        get() = arrayListOf(
            IrFunctionImpl(
                "equals", this, IrAccessModifier.PUBLIC,
                mutableListOf(IrValueParameterImpl("other", type)), BooleanClass.type
            ),
            constructor
        )
    override val type: IrConcreteType get() = IrBuiltinTypes.ANY
    override val allSuperClasses: Set<IrClass> get() = hashSetOf()
    override fun generateValueArgumentFor(random: Random, clazz: IrClass): IrExpression {
        return IrConstructorCallExpressionImpl(constructor, emptyList())
    }
}