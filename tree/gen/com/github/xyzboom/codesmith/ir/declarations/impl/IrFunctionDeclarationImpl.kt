

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode")

package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor
import com.github.xyzboom.codesmith.ir.visitors.transformInplace

internal class IrFunctionDeclarationImpl(
    override var name: String,
    override var language: Language,
    override val typeParameters: MutableList<IrTypeParameter>,
) : IrFunctionDeclaration() {

    override fun <R, D> acceptChildren(visitor: IrVisitor<R, D>, data: D) {
        typeParameters.forEach { it.accept(visitor, data) }
    }

    override fun <D> transformChildren(transformer: IrTransformer<D>, data: D): IrFunctionDeclarationImpl {
        transformTypeParameters(transformer, data)
        return this
    }

    override fun <D> transformName(transformer: IrTransformer<D>, data: D): IrFunctionDeclarationImpl {
        return this
    }

    override fun <D> transformLanguage(transformer: IrTransformer<D>, data: D): IrFunctionDeclarationImpl {
        return this
    }

    override fun <D> transformTypeParameters(transformer: IrTransformer<D>, data: D): IrFunctionDeclarationImpl {
        typeParameters.transformInplace(transformer, data)
        return this
    }

    override fun replaceName(newName: String) {
        name = newName
    }

    override fun replaceLanguage(newLanguage: Language) {
        language = newLanguage
    }

    override fun replaceTypeParameters(newTypeParameters: List<IrTypeParameter>) {
        if (typeParameters === newTypeParameters) return
        typeParameters.clear()
        typeParameters.addAll(newTypeParameters)
    }
}
