

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode")

package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

internal class IrFunctionDeclarationImpl(
    override var name: String,
) : IrFunctionDeclaration() {

    override fun <R, D> acceptChildren(visitor: IrVisitor<R, D>, data: D) {}

    override fun <D> transformChildren(transformer: IrTransformer<D>, data: D): IrFunctionDeclarationImpl {
        return this
    }

    override fun <D> transformName(transformer: IrTransformer<D>, data: D): IrFunctionDeclarationImpl {
        return this
    }

    override fun replaceName(newName: String) {
        name = newName
    }
}
