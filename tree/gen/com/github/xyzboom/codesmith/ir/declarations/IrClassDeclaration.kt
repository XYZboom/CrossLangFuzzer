

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
    abstract override val name: String
    abstract override val language: Language
    abstract override val functions: List<IrFunctionDeclaration>
    abstract override val typeParameters: List<IrTypeParameter>
    abstract val classKind: ClassKind
    abstract val superType: IrType?
    abstract val allSuperTypeArguments: MutableMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>>
    abstract val implementedTypes: List<IrType>

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitClassDeclaration(this, data)

    @Suppress("UNCHECKED_CAST")
    override fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformClassDeclaration(this, data) as E

    abstract override fun replaceName(newName: String)

    abstract override fun replaceLanguage(newLanguage: Language)

    abstract override fun replaceFunctions(newFunctions: List<IrFunctionDeclaration>)

    abstract override fun replaceTypeParameters(newTypeParameters: List<IrTypeParameter>)

    abstract fun replaceClassKind(newClassKind: ClassKind)

    abstract fun replaceSuperType(newSuperType: IrType?)

    abstract fun replaceAllSuperTypeArguments(newAllSuperTypeArguments: MutableMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>>)

    abstract fun replaceImplementedTypes(newImplementedTypes: List<IrType>)

    abstract override fun <D> transformName(transformer: IrTransformer<D>, data: D): IrClassDeclaration

    abstract override fun <D> transformLanguage(transformer: IrTransformer<D>, data: D): IrClassDeclaration

    abstract override fun <D> transformFunctions(transformer: IrTransformer<D>, data: D): IrClassDeclaration

    abstract override fun <D> transformTypeParameters(transformer: IrTransformer<D>, data: D): IrClassDeclaration

    abstract fun <D> transformClassKind(transformer: IrTransformer<D>, data: D): IrClassDeclaration

    abstract fun <D> transformSuperType(transformer: IrTransformer<D>, data: D): IrClassDeclaration

    abstract fun <D> transformAllSuperTypeArguments(transformer: IrTransformer<D>, data: D): IrClassDeclaration

    abstract fun <D> transformImplementedTypes(transformer: IrTransformer<D>, data: D): IrClassDeclaration
}
