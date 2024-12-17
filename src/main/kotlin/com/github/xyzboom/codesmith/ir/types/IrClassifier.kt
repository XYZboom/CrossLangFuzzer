package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration

sealed class IrClassifier(val classDecl: IrClassDeclaration): IrType() {
}