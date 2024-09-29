package com.github.xyzboom.codesmith.ir.types.builtin

import com.github.xyzboom.codesmith.ir.declarations.builtin.AnyClass
import com.github.xyzboom.codesmith.ir.declarations.builtin.Function0Class
import com.github.xyzboom.codesmith.ir.declarations.builtin.Function1Class
import com.github.xyzboom.codesmith.ir.declarations.builtin.NumberClass
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.impl.IrConcreteTypeImpl
import com.github.xyzboom.codesmith.ir.types.impl.IrTypeParameterImpl


object IrBuiltinTypes {
    private const val BUILTIN = "<built-in: %s>"
    val ANY = IrConcreteTypeImpl(BUILTIN.format("Any"), AnyClass)
    val NUMBER = IrConcreteTypeImpl(BUILTIN.format("Number"), AnyClass)
    val INT = IrConcreteTypeImpl(BUILTIN.format("Int"), NumberClass)
    val LONG = IrConcreteTypeImpl(BUILTIN.format("Long"), NumberClass)
    val FLOAT = IrConcreteTypeImpl(BUILTIN.format("Float"), NumberClass)
    val DOUBLE = IrConcreteTypeImpl(BUILTIN.format("Double"), NumberClass)
    val BOOLEAN = IrConcreteTypeImpl(BUILTIN.format("Boolean"), AnyClass)
    val NOTHING = IrConcreteTypeImpl(BUILTIN.format("Nothing"), AnyClass)
    val FUNCTION0 = IrConcreteTypeImpl(
        BUILTIN.format("Function0"),
        Function0Class,
        arguments = listOf(IrTypeParameterImpl(ANY, "R")),
        classType = IrClassType.INTERFACE
    )
    val FUNCTION1 = IrConcreteTypeImpl(
        BUILTIN.format("Function1"),
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