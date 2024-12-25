package com.github.xyzboom.codesmith.ir.expressions

import com.fasterxml.jackson.annotation.JsonIdentityInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.ObjectIdGenerators
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator::class)
abstract class IrExpression : IrElement() {
    open var type: IrType? = null
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
        return visitor.visitExpression(this, data)
    }
}