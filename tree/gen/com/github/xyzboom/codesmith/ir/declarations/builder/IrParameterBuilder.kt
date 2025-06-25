

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "unused")

package com.github.xyzboom.codesmith.ir.declarations.builder

import com.github.xyzboom.codesmith.ir.builder.BuilderDsl
import com.github.xyzboom.codesmith.ir.declarations.IrParameter
import com.github.xyzboom.codesmith.ir.declarations.impl.IrParameterImpl
import com.github.xyzboom.codesmith.ir.expressions.IrExpression
import com.github.xyzboom.codesmith.ir.types.IrType
import kotlin.contracts.*

@BuilderDsl
class IrParameterBuilder {
    lateinit var name: String
    lateinit var type: IrType
    var defaultValue: IrExpression? = null

    fun build(): IrParameter {
        return IrParameterImpl(
            name,
            type,
            defaultValue,
        )
    }

}

@OptIn(ExperimentalContracts::class)
inline fun buildParameter(init: IrParameterBuilder.() -> Unit): IrParameter {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }
    return IrParameterBuilder().apply(init).build()
}
