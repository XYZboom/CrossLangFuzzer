

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.IrParameterList
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.containers.IrTypeParameterContainer
import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

/**
 * Generated from: [com.github.xyzboom.codesmith.tree.generator.TreeBuilder.funcDecl]
 */
abstract class IrFunctionDeclaration : IrDeclaration(), IrTypeParameterContainer {
    abstract override val name: String
    abstract override val language: Language
    abstract override val typeParameters: List<IrTypeParameter>
    abstract val printNullableAnnotations: Boolean
    abstract val body: IrBlock?
    abstract val isOverride: Boolean
    abstract val isOverrideStub: Boolean
    abstract val override: List<IrFunctionDeclaration>
    abstract val isFinal: Boolean
    abstract val parameterList: IrParameterList
    abstract val returnType: IrType
    abstract val containingClassName: String?

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitFunctionDeclaration(this, data)

    @Suppress("UNCHECKED_CAST")
    override fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformFunctionDeclaration(this, data) as E

    abstract override fun replaceName(newName: String)

    abstract override fun replaceLanguage(newLanguage: Language)

    abstract override fun replaceTypeParameters(newTypeParameters: List<IrTypeParameter>)

    abstract fun replacePrintNullableAnnotations(newPrintNullableAnnotations: Boolean)

    abstract fun replaceBody(newBody: IrBlock?)

    abstract fun replaceIsOverride(newIsOverride: Boolean)

    abstract fun replaceIsOverrideStub(newIsOverrideStub: Boolean)

    abstract fun replaceOverride(newOverride: List<IrFunctionDeclaration>)

    abstract fun replaceIsFinal(newIsFinal: Boolean)

    abstract fun replaceParameterList(newParameterList: IrParameterList)

    abstract fun replaceReturnType(newReturnType: IrType)

    abstract fun replaceContainingClassName(newContainingClassName: String?)

    abstract override fun <D> transformName(transformer: IrTransformer<D>, data: D): IrFunctionDeclaration

    abstract override fun <D> transformLanguage(transformer: IrTransformer<D>, data: D): IrFunctionDeclaration

    abstract override fun <D> transformTypeParameters(transformer: IrTransformer<D>, data: D): IrFunctionDeclaration

    abstract fun <D> transformPrintNullableAnnotations(transformer: IrTransformer<D>, data: D): IrFunctionDeclaration

    abstract fun <D> transformBody(transformer: IrTransformer<D>, data: D): IrFunctionDeclaration

    abstract fun <D> transformIsOverride(transformer: IrTransformer<D>, data: D): IrFunctionDeclaration

    abstract fun <D> transformIsOverrideStub(transformer: IrTransformer<D>, data: D): IrFunctionDeclaration

    abstract fun <D> transformOverride(transformer: IrTransformer<D>, data: D): IrFunctionDeclaration

    abstract fun <D> transformIsFinal(transformer: IrTransformer<D>, data: D): IrFunctionDeclaration

    abstract fun <D> transformParameterList(transformer: IrTransformer<D>, data: D): IrFunctionDeclaration

    abstract fun <D> transformReturnType(transformer: IrTransformer<D>, data: D): IrFunctionDeclaration

    abstract fun <D> transformContainingClassName(transformer: IrTransformer<D>, data: D): IrFunctionDeclaration
}
