package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.ir.IrElement

abstract class IrDeclaration(
    val name: String,
): IrElement() {
    var language = Language.KOTLIN
}