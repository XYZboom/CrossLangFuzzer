@file:Suppress("DuplicatedCode", "unused")

package com.github.xyzboom.codesmith.ir.types.builder

import com.github.xyzboom.codesmith.ir.builder.BuilderDsl
import com.github.xyzboom.codesmith.ir.types.IrPlatformType
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.impl.IrPlatformTypeImpl
import com.github.xyzboom.codesmith.ir.types.notPlatformType
import kotlin.contracts.*

@BuilderDsl
class IrPlatformTypeBuilder {
    private lateinit var _innerType: IrType
    var innerType: IrType
        get() = _innerType
        set(value) {
            _innerType = value.notPlatformType
        }

    fun build(): IrPlatformType {
        return IrPlatformTypeImpl(
            innerType,
        )
    }
}

@OptIn(ExperimentalContracts::class)
inline fun buildPlatformType(init: IrPlatformTypeBuilder.() -> Unit): IrPlatformType {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }
    return IrPlatformTypeBuilder().apply(init).build()
}
