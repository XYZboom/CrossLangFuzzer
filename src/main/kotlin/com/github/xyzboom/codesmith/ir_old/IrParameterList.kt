package com.github.xyzboom.codesmith.ir_old

import com.github.xyzboom.codesmith.ir_old.declarations.IrParameter
import com.github.xyzboom.codesmith.ir_old.visitor.IrVisitor

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