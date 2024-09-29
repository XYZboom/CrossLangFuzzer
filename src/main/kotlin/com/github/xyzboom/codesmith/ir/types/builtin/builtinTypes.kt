package com.github.xyzboom.codesmith.ir.types.builtin

import com.github.xyzboom.codesmith.ir.declarations.builtin.AnyClass
import com.github.xyzboom.codesmith.ir.declarations.builtin.Function0Class
import com.github.xyzboom.codesmith.ir.declarations.builtin.Function1Class
import com.github.xyzboom.codesmith.ir.declarations.builtin.NumberClass
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.impl.IrConcreteTypeImpl
import com.github.xyzboom.codesmith.ir.types.impl.IrFunctionTypeImpl
import com.github.xyzboom.codesmith.ir.types.impl.IrTypeParameterImpl


object IrBuiltinTypes {
    val ANY = IrConcreteTypeImpl("Any", AnyClass)
    val NUMBER = IrConcreteTypeImpl("Number", AnyClass)
    val INT = IrConcreteTypeImpl("Int", NumberClass)
    val LONG = IrConcreteTypeImpl("Long", NumberClass)
    val FLOAT = IrConcreteTypeImpl("Float", NumberClass)
    val DOUBLE = IrConcreteTypeImpl("Double", NumberClass)
    val BOOLEAN = IrConcreteTypeImpl("Boolean", AnyClass)
    val NOTHING = IrConcreteTypeImpl("Nothing", AnyClass)
    val FUNCTION0 = IrFunctionTypeImpl(
        "Function0",
        Function0Class,
        arguments = listOf(IrTypeParameterImpl(ANY, "R")),
        classType = IrClassType.INTERFACE
    )
    val FUNCTION1 = IrFunctionTypeImpl(
        "Function1",
        Function1Class,
        arguments = listOf(IrTypeParameterImpl(ANY, "T"), IrTypeParameterImpl(ANY, "R")),
        classType = IrClassType.INTERFACE
    )
    val builtinTypes = listOf(
        ANY,
        NUMBER,
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        BOOLEAN,
        NOTHING,
        FUNCTION0,
        FUNCTION1,
    )
}