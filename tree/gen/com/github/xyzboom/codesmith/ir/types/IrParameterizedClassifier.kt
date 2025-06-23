

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

/**
 * Generated from: [com.github.xyzboom.codesmith.tree.generator.TreeBuilder.parameterizedClassifier]
 */
abstract class IrParameterizedClassifier : IrClassifier() {
    abstract override val classDecl: IrClassDeclaration
    abstract val arguments: HashMap<IrTypeParameter, IrType>

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitParameterizedClassifier(this, data)

    @Suppress("UNCHECKED_CAST")
    override fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformParameterizedClassifier(this, data) as E
}
