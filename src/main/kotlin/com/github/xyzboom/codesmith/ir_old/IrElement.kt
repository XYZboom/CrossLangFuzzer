package com.github.xyzboom.codesmith.ir_old

import com.fasterxml.jackson.annotation.JsonIdentityInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.ObjectIdGenerators
import com.github.xyzboom.codesmith.ir_old.visitor.IrVisitor

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator::class)
abstract class IrElement {
    open fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
        return visitor.visitElement(this, data)
    }

    open fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {}
}