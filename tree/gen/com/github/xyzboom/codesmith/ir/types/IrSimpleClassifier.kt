

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

/**
 * Generated from: [com.github.xyzboom.codesmith.tree.generator.TreeBuilder.simpleClassifier]
 */
abstract class IrSimpleClassifier : IrClassifier() {
    abstract override val classKind: ClassKind
    abstract override val classDecl: IrClassDeclaration

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitSimpleClassifier(this, data)

    @Suppress("UNCHECKED_CAST")
    override fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformSimpleClassifier(this, data) as E

    abstract override fun replaceClassDecl(newClassDecl: IrClassDeclaration)

    abstract override fun <D> transformClassDecl(transformer: IrTransformer<D>, data: D): IrSimpleClassifier
}
