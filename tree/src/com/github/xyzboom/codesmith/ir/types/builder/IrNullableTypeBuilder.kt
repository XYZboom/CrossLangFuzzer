@file:Suppress("DuplicatedCode", "unused")

package com.github.xyzboom.codesmith.ir.types.builder

import com.github.xyzboom.codesmith.ir.builder.BuilderDsl
import com.github.xyzboom.codesmith.ir.types.IrNullableType
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.impl.IrNullableTypeImpl
import kotlin.contracts.*

@BuilderDsl
class IrNullableTypeBuilder {
    private lateinit var _innerType: IrType
    var innerType: IrType
        get() = _innerType
        set(value) {
            _innerType = if (value is IrNullableType) {
                value.innerType
            } else {
                value
            }
        }

    fun build(): IrNullableType {
        return IrNullableTypeImpl(
            innerType,
        )
    }
}

@OptIn(ExperimentalContracts::class)
inline fun buildNullableType(init: IrNullableTypeBuilder.() -> Unit): IrNullableType {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }
    return IrNullableTypeBuilder().apply(init).build()
}
