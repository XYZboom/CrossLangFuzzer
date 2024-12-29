package com.github.xyzboom.codesmith.ir.types

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.github.xyzboom.codesmith.ir.IrElement

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
abstract class IrType : IrElement() {
    @get:JsonIgnore
    abstract val classType: IrClassType
    @get:JsonIgnore
    open val unfinished: Boolean get() = false
    abstract fun equalsIgnoreTypeArguments(other: IrType): Boolean
    open fun copy(): IrType = this
}