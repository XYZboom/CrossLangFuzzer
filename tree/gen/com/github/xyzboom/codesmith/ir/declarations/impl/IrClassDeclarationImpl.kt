

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode")

package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor
import com.github.xyzboom.codesmith.ir.visitors.transformInplace

internal class IrClassDeclarationImpl(
    override val functions: MutableList<IrFunctionDeclaration>,
    override val typeParameters: MutableList<IrTypeParameter>,
) : IrClassDeclaration() {

    override fun <R, D> acceptChildren(visitor: IrVisitor<R, D>, data: D) {
        functions.forEach { it.accept(visitor, data) }
        typeParameters.forEach { it.accept(visitor, data) }
    }

    override fun <D> transformChildren(transformer: IrTransformer<D>, data: D): IrClassDeclarationImpl {
        functions.transformInplace(transformer, data)
        typeParameters.transformInplace(transformer, data)
        return this
    }
}
