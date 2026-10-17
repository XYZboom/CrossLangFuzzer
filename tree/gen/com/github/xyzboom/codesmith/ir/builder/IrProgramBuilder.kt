

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "unused")

package com.github.xyzboom.codesmith.ir.builder

import com.github.xyzboom.codesmith.ir.IrImplementationDetail
import com.github.xyzboom.codesmith.ir.IrModule
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.impl.IrProgramImpl
import kotlin.contracts.*

@BuilderDsl
class IrProgramBuilder {
    val modules: MutableList<IrModule> = mutableListOf()

    @OptIn(IrImplementationDetail::class)
    fun build(): IrProgram {
        return IrProgramImpl(
            modules,
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
