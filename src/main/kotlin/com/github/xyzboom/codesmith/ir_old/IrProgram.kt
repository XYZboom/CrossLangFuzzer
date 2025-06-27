package com.github.xyzboom.codesmith.ir_old

import com.github.xyzboom.codesmith.LanguageOld
import com.github.xyzboom.codesmith.ir_old.container.IrContainer
import com.github.xyzboom.codesmith.ir_old.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir_old.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir_old.declarations.IrPropertyDeclaration

class IrProgram(
    majorLanguage: LanguageOld = LanguageOld.KOTLIN
) : IrElement(), IrContainer {
    var majorLanguage = majorLanguage
        set(value) {
            listOf(functions, classes, properties).forEach {
                it.forEach { decl ->
                    if (decl.language == field) {
                        decl.language = value
                    }
                }
            }
            field = value
        }

    /**
     * Top-level functions
     */
    override val functions = mutableListOf<IrFunctionDeclaration>()

    /**
     * Top-level classes
     */
    override val classes = mutableListOf<IrClassDeclaration>()

    /**
     * Properties can be top level. Top-level properties in Java will be renamed to getXXX.
     */
    override val properties = mutableListOf<IrPropertyDeclaration>()

    override var superContainer: IrContainer? = null
}