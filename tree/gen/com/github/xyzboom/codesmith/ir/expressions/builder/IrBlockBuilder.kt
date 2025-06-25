

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "unused")

package com.github.xyzboom.codesmith.ir.expressions.builder

import com.github.xyzboom.codesmith.ir.builder.BuilderDsl
import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.expressions.IrExpression
import com.github.xyzboom.codesmith.ir.expressions.impl.IrBlockImpl
import kotlin.contracts.*

@BuilderDsl
class IrBlockBuilder {
    val expressions: MutableList<IrExpression> = mutableListOf()

    fun build(): IrBlock {
        return IrBlockImpl(
            expressions,
        )
    }
}

@OptIn(ExperimentalContracts::class)
inline fun buildBlock(init: IrBlockBuilder.() -> Unit = {}): IrBlock {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }
    return IrBlockBuilder().apply(init).build()
}
