package com.github.xyzboom.codesmith.ir.declarations

sealed interface IrFunctionContainer {
    val functions: MutableList<IrFunction>
}