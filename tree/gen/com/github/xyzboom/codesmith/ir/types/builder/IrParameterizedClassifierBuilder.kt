

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "unused")

package com.github.xyzboom.codesmith.ir.types.builder

import com.github.xyzboom.codesmith.ir.builder.BuilderDsl
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.impl.IrParameterizedClassifierImpl
import kotlin.contracts.*

@BuilderDsl
class IrParameterizedClassifierBuilder {
    lateinit var classDecl: IrClassDeclaration
    lateinit var arguments: HashMap<IrTypeParameter, IrType>

    fun build(): IrParameterizedClassifier {
        return IrParameterizedClassifierImpl(
            classDecl,
            arguments,
        )
    }
}

@OptIn(ExperimentalContracts::class)
inline fun buildParameterizedClassifier(init: IrParameterizedClassifierBuilder.() -> Unit): IrParameterizedClassifier {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }
    return IrParameterizedClassifierBuilder().apply(init).build()
}
