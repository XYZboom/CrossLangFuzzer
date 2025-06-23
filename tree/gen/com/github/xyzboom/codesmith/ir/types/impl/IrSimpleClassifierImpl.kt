

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode")

package com.github.xyzboom.codesmith.ir.types.impl

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.types.IrSimpleClassifier
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

internal class IrSimpleClassifierImpl(
    override var classDecl: IrClassDeclaration,
) : IrSimpleClassifier() {

    override fun <R, D> acceptChildren(visitor: IrVisitor<R, D>, data: D) {
        classDecl.accept(visitor, data)
    }

    override fun <D> transformChildren(transformer: IrTransformer<D>, data: D): IrSimpleClassifierImpl {
        classDecl = classDecl.transform(transformer, data)
        return this
    }
}
