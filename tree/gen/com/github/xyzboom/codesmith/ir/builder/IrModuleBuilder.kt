

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "unused")

package com.github.xyzboom.codesmith.ir.builder

import com.github.xyzboom.codesmith.ir.IrModule
import com.github.xyzboom.codesmith.ir.containers.builder.IrFuncContainerBuilder
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrPropertyDeclaration
import com.github.xyzboom.codesmith.ir.impl.IrModuleImpl
import kotlin.contracts.*

@BuilderDsl
class IrModuleBuilder : IrFuncContainerBuilder {
    lateinit var name: String
    val classes: MutableList<IrClassDeclaration> = mutableListOf()
    override val functions: MutableList<IrFunctionDeclaration> = mutableListOf()
    val properties: MutableList<IrPropertyDeclaration> = mutableListOf()
    val dependencies: MutableList<IrModule> = mutableListOf()

    override fun build(): IrModule {
        return IrModuleImpl(
            name,
            classes,
            functions,
            properties,
            dependencies,
        )
    }
}

@OptIn(ExperimentalContracts::class)
inline fun buildModule(init: IrModuleBuilder.() -> Unit): IrModule {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }
    return IrModuleBuilder().apply(init).build()
}
