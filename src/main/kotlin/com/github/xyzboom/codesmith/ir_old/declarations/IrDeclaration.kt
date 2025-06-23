package com.github.xyzboom.codesmith.ir_old.declarations

import com.fasterxml.jackson.annotation.JsonIgnore
import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.ir_old.IrElement
import com.github.xyzboom.codesmith.ir_old.visitor.IrVisitor

abstract class IrDeclaration(
    val name: String,
): IrElement() {
    /**
     * As we can change the language at any time, serialization for [language] is useless.
     */
    @get:JsonIgnore
    @set:JsonIgnore
    @field:JsonIgnore
    var language = Language.KOTLIN
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
        return visitor.visitDeclaration(this, data)
    }
}