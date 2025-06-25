

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.containers.IrTypeParameterContainer
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

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitFunctionDeclaration(this, data)

    @Suppress("UNCHECKED_CAST")
    override fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformFunctionDeclaration(this, data) as E

    abstract override fun replaceName(newName: String)

    abstract override fun replaceLanguage(newLanguage: Language)

    abstract override fun replaceTypeParameters(newTypeParameters: List<IrTypeParameter>)

    abstract override fun <D> transformName(transformer: IrTransformer<D>, data: D): IrFunctionDeclaration

    abstract override fun <D> transformLanguage(transformer: IrTransformer<D>, data: D): IrFunctionDeclaration

    abstract override fun <D> transformTypeParameters(transformer: IrTransformer<D>, data: D): IrFunctionDeclaration
}
