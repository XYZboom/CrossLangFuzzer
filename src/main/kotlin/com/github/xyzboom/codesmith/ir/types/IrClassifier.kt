package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration

sealed class IrClassifier(val classDecl: IrClassDeclaration) : IrType() {
    override val classType: IrClassType = classDecl.classType
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IrClassifier) return false

        if (classDecl != other.classDecl) return false

        return true
    }

    override fun hashCode(): Int {
        return classDecl.hashCode()
    }

    final override fun equalsIgnoreTypeArguments(other: IrType): Boolean {
        if (other !is IrClassifier) return false

        return classDecl == other.classDecl
    }
}