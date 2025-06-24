

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode")

package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor
import com.github.xyzboom.codesmith.ir.visitors.transformInplace

internal class IrClassDeclarationImpl(
    override var name: String,
    override val functions: MutableList<IrFunctionDeclaration>,
    override val typeParameters: MutableList<IrTypeParameter>,
    override var classKind: ClassKind,
) : IrClassDeclaration() {

    override fun <R, D> acceptChildren(visitor: IrVisitor<R, D>, data: D) {
        functions.forEach { it.accept(visitor, data) }
        typeParameters.forEach { it.accept(visitor, data) }
    }

    override fun <D> transformChildren(transformer: IrTransformer<D>, data: D): IrClassDeclarationImpl {
        transformFunctions(transformer, data)
        transformTypeParameters(transformer, data)
        return this
    }

    override fun <D> transformName(transformer: IrTransformer<D>, data: D): IrClassDeclarationImpl {
        return this
    }

    override fun <D> transformFunctions(transformer: IrTransformer<D>, data: D): IrClassDeclarationImpl {
        functions.transformInplace(transformer, data)
        return this
    }

    override fun <D> transformTypeParameters(transformer: IrTransformer<D>, data: D): IrClassDeclarationImpl {
        typeParameters.transformInplace(transformer, data)
        return this
    }

    override fun <D> transformClassKind(transformer: IrTransformer<D>, data: D): IrClassDeclarationImpl {
        return this
    }

    override fun replaceName(newName: String) {
        name = newName
    }

    override fun replaceFunctions(newFunctions: List<IrFunctionDeclaration>) {
        if (functions === newFunctions) return
        functions.clear()
        functions.addAll(newFunctions)
    }

    override fun replaceTypeParameters(newTypeParameters: List<IrTypeParameter>) {
        if (typeParameters === newTypeParameters) return
        typeParameters.clear()
        typeParameters.addAll(newTypeParameters)
    }

    override fun replaceClassKind(newClassKind: ClassKind) {
        classKind = newClassKind
    }
}
