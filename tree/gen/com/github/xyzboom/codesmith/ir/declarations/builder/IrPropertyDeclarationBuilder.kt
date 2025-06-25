

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "unused")

package com.github.xyzboom.codesmith.ir.declarations.builder

import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.builder.BuilderDsl
import com.github.xyzboom.codesmith.ir.declarations.IrPropertyDeclaration
import com.github.xyzboom.codesmith.ir.declarations.impl.IrPropertyDeclarationImpl
import kotlin.contracts.*

@BuilderDsl
class IrPropertyDeclarationBuilder {
    lateinit var name: String
    lateinit var language: Language

    fun build(): IrPropertyDeclaration {
        return IrPropertyDeclarationImpl(
            name,
            language,
        )
    }
}

@OptIn(ExperimentalContracts::class)
inline fun buildPropertyDeclaration(init: IrPropertyDeclarationBuilder.() -> Unit): IrPropertyDeclaration {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }
    return IrPropertyDeclarationBuilder().apply(init).build()
}
