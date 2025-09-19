

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode")

package com.github.xyzboom.codesmith.ir.types.impl

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.types.IrDefinitelyNotNullType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

internal class IrDefinitelyNotNullTypeImpl(
    override var innerType: IrTypeParameter,
) : IrDefinitelyNotNullType() {
    override val classKind: ClassKind
        get() = innerType.classKind

    override fun <R, D> acceptChildren(visitor: IrVisitor<R, D>, data: D) {
        innerType.accept(visitor, data)
    }

    override fun <D> transformChildren(transformer: IrTransformer<D>, data: D): IrDefinitelyNotNullTypeImpl {
        transformInnerType(transformer, data)
        return this
    }

    override fun <D> transformInnerType(transformer: IrTransformer<D>, data: D): IrDefinitelyNotNullTypeImpl {
        innerType = innerType.transform(transformer, data)
        return this
    }
}
