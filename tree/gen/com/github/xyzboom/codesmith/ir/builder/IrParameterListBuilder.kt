

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "unused")

package com.github.xyzboom.codesmith.ir.builder

import com.github.xyzboom.codesmith.ir.IrParameterList
import com.github.xyzboom.codesmith.ir.declarations.IrParameter
import com.github.xyzboom.codesmith.ir.impl.IrParameterListImpl
import kotlin.contracts.*

@BuilderDsl
class IrParameterListBuilder {
    val parameters: MutableList<IrParameter> = mutableListOf()

    fun build(): IrParameterList {
        return IrParameterListImpl(
            parameters,
        )
    }
}

@OptIn(ExperimentalContracts::class)
inline fun buildParameterList(init: IrParameterListBuilder.() -> Unit = {}): IrParameterList {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }
    return IrParameterListBuilder().apply(init).build()
}
