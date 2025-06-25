

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

/**
 * Generated from: [com.github.xyzboom.codesmith.tree.generator.TreeBuilder.typeParameter]
 */
abstract class IrTypeParameter : IrType() {
    abstract override val classKind: ClassKind
    abstract val name: String
    abstract val upperbound: IrType

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitTypeParameter(this, data)

    @Suppress("UNCHECKED_CAST")
    override fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformTypeParameter(this, data) as E

    abstract fun replaceName(newName: String)

    abstract fun replaceUpperbound(newUpperbound: IrType)

    abstract fun <D> transformName(transformer: IrTransformer<D>, data: D): IrTypeParameter

    abstract fun <D> transformUpperbound(transformer: IrTransformer<D>, data: D): IrTypeParameter
}
