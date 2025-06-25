

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode")

package com.github.xyzboom.codesmith.ir.types.impl

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.IrTypeParameterName
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

internal class IrParameterizedClassifierImpl(
    override var classDecl: IrClassDeclaration,
    override var arguments: MutableMap<IrTypeParameterName, Pair<IrTypeParameter, IrType?>>,
) : IrParameterizedClassifier() {
    override val classKind: ClassKind
        get() = classDecl.classKind

    override fun <R, D> acceptChildren(visitor: IrVisitor<R, D>, data: D) {
        classDecl.accept(visitor, data)
    }

    override fun <D> transformChildren(transformer: IrTransformer<D>, data: D): IrParameterizedClassifierImpl {
        transformClassDecl(transformer, data)
        return this
    }

    override fun <D> transformClassDecl(transformer: IrTransformer<D>, data: D): IrParameterizedClassifierImpl {
        classDecl = classDecl.transform(transformer, data)
        return this
    }

    override fun <D> transformArguments(transformer: IrTransformer<D>, data: D): IrParameterizedClassifierImpl {
        return this
    }

    override fun replaceClassDecl(newClassDecl: IrClassDeclaration) {
        classDecl = newClassDecl
    }

    override fun replaceArguments(newArguments: MutableMap<IrTypeParameterName, Pair<IrTypeParameter, IrType?>>) {
        arguments = newArguments
    }
}
