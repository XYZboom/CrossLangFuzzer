

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "unused")

package com.github.xyzboom.codesmith.ir.declarations.builder

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.builder.BuilderDsl
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.impl.IrClassDeclarationImpl
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import kotlin.contracts.*

@BuilderDsl
class IrClassDeclarationBuilder {
    lateinit var name: String
    val functions: MutableList<IrFunctionDeclaration> = mutableListOf()
    val typeParameters: MutableList<IrTypeParameter> = mutableListOf()
    lateinit var classKind: ClassKind

    fun build(): IrClassDeclaration {
        return IrClassDeclarationImpl(
            name,
            functions,
            typeParameters,
            classKind,
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
