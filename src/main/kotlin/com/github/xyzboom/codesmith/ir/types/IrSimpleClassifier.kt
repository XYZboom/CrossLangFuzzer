package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration

class IrSimpleClassifier(
    classDecl: IrClassDeclaration,
): IrClassifier(classDecl) {
    override fun toString(): String {
        return classDecl.toString()
    }




}