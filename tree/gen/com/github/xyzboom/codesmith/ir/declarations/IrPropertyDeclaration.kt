

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

/**
 * Generated from: [com.github.xyzboom.codesmith.tree.generator.TreeBuilder.propertyDecl]
 */
abstract class IrPropertyDeclaration : IrDeclaration() {
    abstract override val name: String
    abstract override val language: Language

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitPropertyDeclaration(this, data)

    @Suppress("UNCHECKED_CAST")
    override fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformPropertyDeclaration(this, data) as E

    abstract override fun replaceName(newName: String)

    abstract override fun replaceLanguage(newLanguage: Language)

    abstract override fun <D> transformName(transformer: IrTransformer<D>, data: D): IrPropertyDeclaration

    abstract override fun <D> transformLanguage(transformer: IrTransformer<D>, data: D): IrPropertyDeclaration
}
