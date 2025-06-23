

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "unused")

package com.github.xyzboom.codesmith.ir.types.builder

import com.github.xyzboom.codesmith.ir.builder.BuilderDsl
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.types.IrSimpleClassifier
import com.github.xyzboom.codesmith.ir.types.impl.IrSimpleClassifierImpl
import kotlin.contracts.*

@BuilderDsl
class IrSimpleClassifierBuilder {
    lateinit var classDecl: IrClassDeclaration

    fun build(): IrSimpleClassifier {
        return IrSimpleClassifierImpl(
            classDecl,
        )
    }
}

@OptIn(ExperimentalContracts::class)
inline fun buildSimpleClassifier(init: IrSimpleClassifierBuilder.() -> Unit): IrSimpleClassifier {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }
    return IrSimpleClassifierBuilder().apply(init).build()
}
