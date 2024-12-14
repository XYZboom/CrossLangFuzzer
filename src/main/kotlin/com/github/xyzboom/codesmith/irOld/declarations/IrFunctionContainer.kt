package com.github.xyzboom.codesmith.irOld.declarations

sealed interface IrFunctionContainer {
    val functions: MutableList<IrFunction>
}