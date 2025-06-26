

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode")

package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.IrParameterList
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor
import com.github.xyzboom.codesmith.ir.visitors.transformInplace

internal class IrFunctionDeclarationImpl(
    override var name: String,
    override var language: Language,
    override val typeParameters: MutableList<IrTypeParameter>,
    override var printNullableAnnotations: Boolean,
    override var body: IrBlock?,
    override var isOverride: Boolean,
    override var isOverrideStub: Boolean,
    override val override: MutableList<IrFunctionDeclaration>,
    override var isFinal: Boolean,
    override var parameterList: IrParameterList,
    override var returnType: IrType,
    override var containingClassName: String?,
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

    override fun <D> transformPrintNullableAnnotations(transformer: IrTransformer<D>, data: D): IrFunctionDeclarationImpl {
        return this
    }

    override fun <D> transformBody(transformer: IrTransformer<D>, data: D): IrFunctionDeclarationImpl {
        body = body?.transform(transformer, data)
        return this
    }

    override fun <D> transformIsOverride(transformer: IrTransformer<D>, data: D): IrFunctionDeclarationImpl {
        return this
    }

    override fun <D> transformIsOverrideStub(transformer: IrTransformer<D>, data: D): IrFunctionDeclarationImpl {
        return this
    }

    override fun <D> transformOverride(transformer: IrTransformer<D>, data: D): IrFunctionDeclarationImpl {
        override.transformInplace(transformer, data)
        return this
    }

    override fun <D> transformIsFinal(transformer: IrTransformer<D>, data: D): IrFunctionDeclarationImpl {
        return this
    }

    override fun <D> transformParameterList(transformer: IrTransformer<D>, data: D): IrFunctionDeclarationImpl {
        parameterList = parameterList.transform(transformer, data)
        return this
    }

    override fun <D> transformReturnType(transformer: IrTransformer<D>, data: D): IrFunctionDeclarationImpl {
        returnType = returnType.transform(transformer, data)
        return this
    }

    override fun <D> transformContainingClassName(transformer: IrTransformer<D>, data: D): IrFunctionDeclarationImpl {
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

    override fun replacePrintNullableAnnotations(newPrintNullableAnnotations: Boolean) {
        printNullableAnnotations = newPrintNullableAnnotations
    }

    override fun replaceBody(newBody: IrBlock?) {
        body = newBody
    }

    override fun replaceIsOverride(newIsOverride: Boolean) {
        isOverride = newIsOverride
    }

    override fun replaceIsOverrideStub(newIsOverrideStub: Boolean) {
        isOverrideStub = newIsOverrideStub
    }

    override fun replaceOverride(newOverride: List<IrFunctionDeclaration>) {
        if (override === newOverride) return
        override.clear()
        override.addAll(newOverride)
    }

    override fun replaceIsFinal(newIsFinal: Boolean) {
        isFinal = newIsFinal
    }

    override fun replaceParameterList(newParameterList: IrParameterList) {
        parameterList = newParameterList
    }

    override fun replaceReturnType(newReturnType: IrType) {
        returnType = newReturnType
    }

    override fun replaceContainingClassName(newContainingClassName: String?) {
        containingClassName = newContainingClassName
    }
}
