package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.declarations.builder.IrFunctionDeclarationBuilder

fun IrFunctionDeclarationBuilder.asString(): String {
    return "${if (body == null) "abstract " else ""}fun $name [" +
            "isOverride=$isOverride, isOverrideStub=$isOverrideStub, isFinal=$isFinal]"
}