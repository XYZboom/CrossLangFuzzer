

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "unused")

package com.github.xyzboom.codesmith.ir.declarations.builder

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.builder.BuilderDsl
import com.github.xyzboom.codesmith.ir.containers.builder.IrFuncContainerBuilder
import com.github.xyzboom.codesmith.ir.containers.builder.IrTypeParameterContainerBuilder
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.impl.IrClassDeclarationImpl
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.IrTypeParameterName
import kotlin.contracts.*

@BuilderDsl
class IrClassDeclarationBuilder : IrTypeParameterContainerBuilder, IrFuncContainerBuilder {
    override lateinit var name: String
    lateinit var language: Language
    override val functions: MutableList<IrFunctionDeclaration> = mutableListOf()
    override val typeParameters: MutableList<IrTypeParameter> = mutableListOf()
    lateinit var classKind: ClassKind
    var superType: IrType? = null
    lateinit var allSuperTypeArguments: MutableMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>>
    val implementedTypes: MutableList<IrType> = mutableListOf()

    override fun build(): IrClassDeclaration {
        return IrClassDeclarationImpl(
            name,
            language,
            functions,
            typeParameters,
            classKind,
            superType,
            allSuperTypeArguments,
            implementedTypes,
        )
    }

}

@OptIn(ExperimentalContracts::class)
inline fun buildClassDeclaration(init: IrClassDeclarationBuilder.() -> Unit): IrClassDeclaration {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }
    return IrClassDeclarationBuilder().apply(init).build()
}
