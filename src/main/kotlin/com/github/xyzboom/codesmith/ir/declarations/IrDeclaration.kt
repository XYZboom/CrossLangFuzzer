package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.IrElement

sealed interface IrDeclaration: IrElement, IrAccessModifierContainer {
}