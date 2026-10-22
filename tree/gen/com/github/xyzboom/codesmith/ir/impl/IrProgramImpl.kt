

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode")

package com.github.xyzboom.codesmith.ir.impl

import com.github.xyzboom.codesmith.ir.*
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor
import com.github.xyzboom.codesmith.ir.visitors.transformInplace

class IrProgramImpl @IrImplementationDetail constructor(
    override var modules: MutableList<IrModule>,
    override var nullabilityAnnotationProvider: NullabilityAnnotationProvider,
) : IrPureAbstractElement(), IrProgram {

    override fun <R, D> acceptChildren(visitor: IrVisitor<R, D>, data: D) {
        modules.forEach { it.accept(visitor, data) }
    }

    override fun <D> transformChildren(transformer: IrTransformer<D>, data: D): IrProgramImpl {
        transformModules(transformer, data)
        return this
    }

    override fun <D> transformModules(transformer: IrTransformer<D>, data: D): IrProgramImpl {
        modules.transformInplace(transformer, data)
        return this
    }

    override fun <D> transformNullabilityAnnotationProvider(transformer: IrTransformer<D>, data: D): IrProgramImpl {
        return this
    }
}
