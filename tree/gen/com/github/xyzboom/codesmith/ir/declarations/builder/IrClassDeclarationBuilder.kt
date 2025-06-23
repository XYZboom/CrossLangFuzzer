

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "unused")

package com.github.xyzboom.codesmith.ir.declarations.builder

import com.github.xyzboom.codesmith.ir.builder.BuilderDsl
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.impl.IrClassDeclarationImpl
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import kotlin.contracts.*

@BuilderDsl
class IrClassDeclarationBuilder {
    val functions: MutableList<IrFunctionDeclaration> = mutableListOf()
    val typeParameters: MutableList<IrTypeParameter> = mutableListOf()

    fun build(): IrClassDeclaration {
        return IrClassDeclarationImpl(
            functions,
            typeParameters,
        )
    }
}

@OptIn(ExperimentalContracts::class)
inline fun buildClassDeclaration(init: IrClassDeclarationBuilder.() -> Unit = {}): IrClassDeclaration {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }
    return IrClassDeclarationBuilder().apply(init).build()
}
