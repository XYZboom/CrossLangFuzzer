package com.github.xyzboom.codesmith.ir

import com.fasterxml.jackson.annotation.JsonIdentityInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.ObjectIdGenerators
import io.github.xyzboom.bf.tree.INode
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator::class)
abstract class IrElement: INode {
    open fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
        return visitor.visitElement(this, data)
    }

    open fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {}
}