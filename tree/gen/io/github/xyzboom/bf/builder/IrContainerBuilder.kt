

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "unused")

package io.github.xyzboom.bf.builder

import io.github.xyzboom.bf.IrContainer
import io.github.xyzboom.bf.declarations.IrClassDeclaration
import io.github.xyzboom.bf.impl.IrContainerImpl
import io.github.xyzboom.crosslangfuzzer.builder.BuilderDsl
import kotlin.contracts.*

@BuilderDsl
class IrContainerBuilder {
    val classes: MutableList<IrClassDeclaration> = mutableListOf()

    fun build(): IrContainer {
        return IrContainerImpl(
            classes,
        )
    }
}

@OptIn(ExperimentalContracts::class)
inline fun buildContainer(init: IrContainerBuilder.() -> Unit = {}): IrContainer {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }
    return IrContainerBuilder().apply(init).build()
}
