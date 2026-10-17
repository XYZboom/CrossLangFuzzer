

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode")

package com.github.xyzboom.codesmith.ir.impl

import com.github.xyzboom.codesmith.ir.IrModule
import com.github.xyzboom.codesmith.ir.IrPureAbstractElement
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrPropertyDeclaration
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor
import com.github.xyzboom.codesmith.ir.visitors.transformInplace

internal class IrModuleImpl(
    override var name: String,
    override var classes: MutableList<IrClassDeclaration>,
    override var functions: MutableList<IrFunctionDeclaration>,
    override var properties: MutableList<IrPropertyDeclaration>,
    override var dependencies: MutableList<IrModule>,
) : IrPureAbstractElement(), IrModule {

    override fun <R, D> acceptChildren(visitor: IrVisitor<R, D>, data: D) {
        classes.forEach { it.accept(visitor, data) }
        functions.forEach { it.accept(visitor, data) }
        properties.forEach { it.accept(visitor, data) }
    }

    override fun <D> transformChildren(transformer: IrTransformer<D>, data: D): IrModuleImpl {
        transformClasses(transformer, data)
        transformFunctions(transformer, data)
        transformProperties(transformer, data)
        return this
    }

    override fun <D> transformName(transformer: IrTransformer<D>, data: D): IrModuleImpl {
        return this
    }

    override fun <D> transformClasses(transformer: IrTransformer<D>, data: D): IrModuleImpl {
        classes.transformInplace(transformer, data)
        return this
    }

    override fun <D> transformFunctions(transformer: IrTransformer<D>, data: D): IrModuleImpl {
        functions.transformInplace(transformer, data)
        return this
    }

    override fun <D> transformProperties(transformer: IrTransformer<D>, data: D): IrModuleImpl {
        properties.transformInplace(transformer, data)
        return this
    }

    override fun <D> transformDependencies(transformer: IrTransformer<D>, data: D): IrModuleImpl {
        dependencies.transformInplace(transformer, data)
        return this
    }
}
