

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode")

package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.declarations.IrPropertyDeclaration
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

internal class IrPropertyDeclarationImpl(
    override var name: String,
    override var language: Language,
) : IrPropertyDeclaration() {

    override fun <R, D> acceptChildren(visitor: IrVisitor<R, D>, data: D) {}

    override fun <D> transformChildren(transformer: IrTransformer<D>, data: D): IrPropertyDeclarationImpl {
        return this
    }

    override fun <D> transformName(transformer: IrTransformer<D>, data: D): IrPropertyDeclarationImpl {
        return this
    }

    override fun <D> transformLanguage(transformer: IrTransformer<D>, data: D): IrPropertyDeclarationImpl {
        return this
    }
}
