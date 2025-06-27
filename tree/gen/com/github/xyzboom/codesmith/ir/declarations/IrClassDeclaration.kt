

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.containers.IrFuncContainer
import com.github.xyzboom.codesmith.ir.containers.IrTypeParameterContainer
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.IrTypeParameterName
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

/**
 * Generated from: [com.github.xyzboom.codesmith.tree.generator.TreeBuilder.classDecl]
 */
abstract class IrClassDeclaration : IrDeclaration(), IrFuncContainer, IrTypeParameterContainer {
    abstract override var name: String
    abstract override var language: Language
    abstract override var functions: MutableList<IrFunctionDeclaration>
    abstract override var typeParameters: MutableList<IrTypeParameter>
    abstract var classKind: ClassKind
    abstract var superType: IrType?
    abstract var allSuperTypeArguments: MutableMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>>
    abstract var implementedTypes: MutableList<IrType>

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitClassDeclaration(this, data)

    @Suppress("UNCHECKED_CAST")
    override fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformClassDeclaration(this, data) as E

    abstract override fun <D> transformName(transformer: IrTransformer<D>, data: D): IrClassDeclaration

    abstract override fun <D> transformLanguage(transformer: IrTransformer<D>, data: D): IrClassDeclaration

    abstract override fun <D> transformFunctions(transformer: IrTransformer<D>, data: D): IrClassDeclaration

    abstract override fun <D> transformTypeParameters(transformer: IrTransformer<D>, data: D): IrClassDeclaration

    abstract fun <D> transformClassKind(transformer: IrTransformer<D>, data: D): IrClassDeclaration

    abstract fun <D> transformSuperType(transformer: IrTransformer<D>, data: D): IrClassDeclaration

    abstract fun <D> transformAllSuperTypeArguments(transformer: IrTransformer<D>, data: D): IrClassDeclaration

    abstract fun <D> transformImplementedTypes(transformer: IrTransformer<D>, data: D): IrClassDeclaration
}
