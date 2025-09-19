

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.IrPureAbstractElement
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

/**
 * Generated from: [com.github.xyzboom.codesmith.tree.generator.TreeBuilder.definitelyNotNullType]
 */
abstract class IrDefinitelyNotNullType : IrPureAbstractElement(), IrType {
    abstract override val classKind: ClassKind
    abstract var innerType: IrTypeParameter

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitDefinitelyNotNullType(this, data)

    @Suppress("UNCHECKED_CAST")
    override fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformDefinitelyNotNullType(this, data) as E

    abstract fun <D> transformInnerType(transformer: IrTransformer<D>, data: D): IrDefinitelyNotNullType
}
