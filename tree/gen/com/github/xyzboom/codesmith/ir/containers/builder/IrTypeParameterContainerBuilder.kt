

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "unused")

package com.github.xyzboom.codesmith.ir.containers.builder

import com.github.xyzboom.codesmith.ir.builder.BuilderDsl
import com.github.xyzboom.codesmith.ir.containers.IrTypeParameterContainer
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter

@BuilderDsl
interface IrTypeParameterContainerBuilder {
    abstract val typeParameters: MutableList<IrTypeParameter>

    fun build(): IrTypeParameterContainer
}
