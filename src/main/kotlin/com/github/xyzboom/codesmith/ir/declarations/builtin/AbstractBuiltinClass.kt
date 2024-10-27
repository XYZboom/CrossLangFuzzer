package com.github.xyzboom.codesmith.ir.declarations.builtin

import com.github.xyzboom.codesmith.ir.IrAccessModifier
import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.declarations.IrCompanionObject
import com.github.xyzboom.codesmith.ir.declarations.impl.IrClassImpl
import com.github.xyzboom.codesmith.ir.declarations.impl.IrFileImpl
import com.github.xyzboom.codesmith.ir.expressions.IrExpression
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrConcreteType
import kotlin.random.Random

abstract class AbstractBuiltinClass(
    name: String,
    superType: IrConcreteType? = null,
    classType: IrClassType = IrClassType.FINAL,
    implementedTypes: MutableList<IrConcreteType> = mutableListOf()
): IrClassImpl(
    name, IrFileImpl.builtin, IrAccessModifier.PUBLIC,
    classType, superType, implementedTypes
) {
    override val companionObject: IrCompanionObject?
        get() = null
    abstract override val type: IrConcreteType
    abstract fun generateValueArgumentFor(random: Random, clazz: IrClass): IrExpression
}