

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "unused")

package com.github.xyzboom.codesmith.ir.types.builder

import com.github.xyzboom.codesmith.ir.builder.BuilderDsl
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.impl.IrTypeParameterImpl
import kotlin.contracts.*

@BuilderDsl
class IrTypeParameterBuilder {
    lateinit var name: String
    lateinit var upperbound: IrType

    fun build(): IrTypeParameter {
        return IrTypeParameterImpl(
            name,
            upperbound,
        )
    }
}

@OptIn(ExperimentalContracts::class)
inline fun buildTypeParameter(init: IrTypeParameterBuilder.() -> Unit): IrTypeParameter {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }
    return IrTypeParameterBuilder().apply(init).build()
}
