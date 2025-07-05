

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.IrPureAbstractElement
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

/**
 * Generated from: [com.github.xyzboom.codesmith.tree.generator.TreeBuilder.classifier]
 */
sealed class IrClassifier : IrPureAbstractElement(), IrType {
    abstract override val classKind: ClassKind
    abstract var classDecl: IrClassDeclaration

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitClassifier(this, data)

    @Suppress("UNCHECKED_CAST")
    override fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformClassifier(this, data) as E

    abstract fun <D> transformClassDecl(transformer: IrTransformer<D>, data: D): IrClassifier
}
