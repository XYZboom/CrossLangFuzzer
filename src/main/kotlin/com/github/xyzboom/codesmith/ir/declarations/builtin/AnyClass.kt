package com.github.xyzboom.codesmith.ir.declarations.builtin

import com.github.xyzboom.codesmith.ir.IrAccessModifier
import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.declarations.IrFunction
import com.github.xyzboom.codesmith.ir.declarations.IrValueParameter
import com.github.xyzboom.codesmith.ir.declarations.impl.IrConstructorImpl
import com.github.xyzboom.codesmith.ir.declarations.impl.IrFunctionImpl
import com.github.xyzboom.codesmith.ir.declarations.impl.IrValueParameterImpl
import com.github.xyzboom.codesmith.ir.expressions.IrConstructorCallExpression.Companion.anyConstructor
import com.github.xyzboom.codesmith.ir.expressions.IrExpression
import com.github.xyzboom.codesmith.ir.expressions.impl.IrConstructorCallExpressionImpl
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrConcreteType
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltinTypes
import kotlin.random.Random

object AnyClass: AbstractBuiltinClass("Any", classType = IrClassType.OPEN) {
    private val constructor = IrConstructorImpl(IrAccessModifier.PUBLIC, this, anyConstructor)
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
    override fun generateValueArgumentFor(random: Random, valueParameter: IrValueParameter): IrExpression {
        return IrConstructorCallExpressionImpl(constructor, emptyList())
    }
}