

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "unused")

package com.github.xyzboom.codesmith.ir.declarations.builder

import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.builder.BuilderDsl
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.impl.IrFunctionDeclarationImpl
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import kotlin.contracts.*

@BuilderDsl
class IrFunctionDeclarationBuilder {
    lateinit var name: String
    lateinit var language: Language
    val typeParameters: MutableList<IrTypeParameter> = mutableListOf()

    fun build(): IrFunctionDeclaration {
        return IrFunctionDeclarationImpl(
            name,
            language,
            typeParameters,
        )
    }
}

@OptIn(ExperimentalContracts::class)
inline fun buildFunctionDeclaration(init: IrFunctionDeclarationBuilder.() -> Unit): IrFunctionDeclaration {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }
    return IrFunctionDeclarationBuilder().apply(init).build()
}
