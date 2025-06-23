package com.github.xyzboom.codesmith.ir_old.declarations

import com.github.xyzboom.codesmith.ir_old.visitor.IrVisitor

class IrFieldDeclaration(name: String): IrDeclaration(name) {
    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {

    }
}