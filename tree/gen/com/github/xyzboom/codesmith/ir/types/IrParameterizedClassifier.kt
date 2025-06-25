

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

/**
 * Generated from: [com.github.xyzboom.codesmith.tree.generator.TreeBuilder.parameterizedClassifier]
 */
abstract class IrParameterizedClassifier : IrClassifier() {
    abstract override val classKind: ClassKind
    abstract override val classDecl: IrClassDeclaration
    abstract val arguments: MutableMap<IrTypeParameterName, Pair<IrTypeParameter, IrType?>>

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitParameterizedClassifier(this, data)

    @Suppress("UNCHECKED_CAST")
    override fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformParameterizedClassifier(this, data) as E

    abstract override fun replaceClassDecl(newClassDecl: IrClassDeclaration)

    abstract fun replaceArguments(newArguments: MutableMap<IrTypeParameterName, Pair<IrTypeParameter, IrType?>>)

    abstract override fun <D> transformClassDecl(transformer: IrTransformer<D>, data: D): IrParameterizedClassifier

    abstract fun <D> transformArguments(transformer: IrTransformer<D>, data: D): IrParameterizedClassifier
}
