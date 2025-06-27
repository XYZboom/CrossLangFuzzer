

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.IrNamedElement
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

/**
 * Generated from: [com.github.xyzboom.codesmith.tree.generator.TreeBuilder.declaration]
 */
abstract class IrDeclaration : IrNamedElement {
    abstract override var name: String
    abstract var language: Language

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitDeclaration(this, data)

    @Suppress("UNCHECKED_CAST")
    override fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformDeclaration(this, data) as E

    abstract override fun <D> transformName(transformer: IrTransformer<D>, data: D): IrDeclaration

    abstract fun <D> transformLanguage(transformer: IrTransformer<D>, data: D): IrDeclaration
}
