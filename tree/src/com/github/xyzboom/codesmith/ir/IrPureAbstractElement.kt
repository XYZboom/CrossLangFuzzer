package com.github.xyzboom.codesmith.ir

import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

abstract class IrPureAbstractElement : IrElement {
    abstract override fun <R, D> acceptChildren(visitor: IrVisitor<R, D>, data: D)

    abstract override fun <D> transformChildren(transformer: IrTransformer<D>, data: D): IrElement
}