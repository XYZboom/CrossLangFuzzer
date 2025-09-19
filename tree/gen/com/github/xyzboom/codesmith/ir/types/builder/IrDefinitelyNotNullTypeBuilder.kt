

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "unused")

package com.github.xyzboom.codesmith.ir.types.builder

import com.github.xyzboom.codesmith.ir.builder.BuilderDsl
import com.github.xyzboom.codesmith.ir.types.IrDefinitelyNotNullType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.impl.IrDefinitelyNotNullTypeImpl
import kotlin.contracts.*

@BuilderDsl
class IrDefinitelyNotNullTypeBuilder {
    lateinit var innerType: IrTypeParameter

    fun build(): IrDefinitelyNotNullType {
        return IrDefinitelyNotNullTypeImpl(
            innerType,
        )
    }
}

@OptIn(ExperimentalContracts::class)
inline fun buildDefinitelyNotNullType(init: IrDefinitelyNotNullTypeBuilder.() -> Unit): IrDefinitelyNotNullType {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }
    return IrDefinitelyNotNullTypeBuilder().apply(init).build()
}
