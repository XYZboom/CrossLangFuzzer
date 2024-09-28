package com.github.xyzboom.codesmith.ir.types.builtin

import com.github.xyzboom.codesmith.ir.types.impl.IrConcreteTypeImpl


private const val builtin = "<built-in: %s>"
val ANY = IrConcreteTypeImpl(builtin.format("Any"))
val NUMBER = IrConcreteTypeImpl(builtin.format("Number"), ANY)
val INT = IrConcreteTypeImpl(builtin.format("Int"), NUMBER)
val LONG = IrConcreteTypeImpl(builtin.format("Long"), NUMBER)
val FLOAT = IrConcreteTypeImpl(builtin.format("Float"), NUMBER)
val DOUBLE = IrConcreteTypeImpl(builtin.format("Double"), NUMBER)
val NOTHING = IrConcreteTypeImpl(builtin.format("Nothing"), ANY)
val builtinTypes = listOf(
    ANY,
    NUMBER,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    NOTHING,
)