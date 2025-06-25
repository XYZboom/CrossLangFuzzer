

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "unused")

package com.github.xyzboom.codesmith.ir.builder

import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrPropertyDeclaration
import com.github.xyzboom.codesmith.ir.impl.IrProgramImpl
import kotlin.contracts.*

@BuilderDsl
class IrProgramBuilder {
    val classes: MutableList<IrClassDeclaration> = mutableListOf()
    val functions: MutableList<IrFunctionDeclaration> = mutableListOf()
    val properties: MutableList<IrPropertyDeclaration> = mutableListOf()

    fun build(): IrProgram {
        return IrProgramImpl(
            classes,
            functions,
            properties,
        )
    }
}

@OptIn(ExperimentalContracts::class)
inline fun buildProgram(init: IrProgramBuilder.() -> Unit = {}): IrProgram {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }
    return IrProgramBuilder().apply(init).build()
}
