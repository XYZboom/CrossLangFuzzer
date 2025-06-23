

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.containers.IrFuncContainer
import com.github.xyzboom.codesmith.ir.containers.IrTypeParameterContainer
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

/**
 * Generated from: [com.github.xyzboom.codesmith.tree.generator.TreeBuilder.classDecl]
 */
abstract class IrClassDeclaration : IrDeclaration(), IrFuncContainer, IrTypeParameterContainer {
    abstract override val functions: List<IrFunctionDeclaration>
    abstract override val typeParameters: List<IrTypeParameter>

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitClassDeclaration(this, data)

    @Suppress("UNCHECKED_CAST")
    override fun <E : IrElement, D> transform(transformer: IrTransformer<D>, data: D): E =
        transformer.transformClassDeclaration(this, data) as E
}
