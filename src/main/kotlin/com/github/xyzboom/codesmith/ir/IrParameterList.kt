package com.github.xyzboom.codesmith.ir

import com.github.xyzboom.codesmith.ir.declarations.IrParameter
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

class IrParameterList : IrElement() {
    val parameters = mutableListOf<IrParameter>()

    fun copyForOverride(): IrParameterList {
        return IrParameterList().also {
            for (param in parameters) {
                it.parameters.add(param.copyForOverride())
            }
        }
    }

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
        return visitor.visitParameterList(this, data)
    }
}