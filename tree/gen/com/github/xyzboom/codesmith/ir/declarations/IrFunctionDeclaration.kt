

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
    abstract override var name: String
    abstract override var language: Language
    abstract override var typeParameters: MutableList<IrTypeParameter>
    abstract var printNullableAnnotations: Boolean
    abstract var body: IrBlock?
    abstract var isOverride: Boolean
    abstract var isOverrideStub: Boolean
    abstract var override: MutableList<IrFunctionDeclaration>
    abstract var isFinal: Boolean
    abstract var parameterList: IrParameterList
    abstract var returnType: IrType
    abstract var containingClassName: String?

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitFunctionDeclaration(this, data)

    @Suppress("UNCHECKED_CAST")
    override fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformFunctionDeclaration(this, data) as E

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
