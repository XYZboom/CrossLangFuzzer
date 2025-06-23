package com.github.xyzboom.codesmith.ir_old.types

import com.github.xyzboom.codesmith.ir_old.declarations.IrClassDeclaration

class IrSimpleClassifier(
    classDecl: IrClassDeclaration,
): IrClassifier(classDecl) {
    override fun toString(): String {
        return classDecl.toString()
    }




}