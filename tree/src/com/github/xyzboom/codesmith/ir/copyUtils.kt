package com.github.xyzboom.codesmith.ir

import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.declarations.IrParameter
import com.github.xyzboom.codesmith.ir.declarations.builder.buildParameter
import com.github.xyzboom.codesmith.ir.types.copy

fun IrParameter.copyForOverride(): IrParameter {
    return buildParameter {
        this.name = this@copyForOverride.name
        this.type = this@copyForOverride.type.copy()
    }
}

fun IrParameterList.copyForOverride(): IrParameterList {
    return buildParameterList {
        for (parameter in this@copyForOverride.parameters) {
            this.parameters.add(parameter.copyForOverride())
        }
    }
}