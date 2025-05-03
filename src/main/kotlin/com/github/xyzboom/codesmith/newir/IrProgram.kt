package com.github.xyzboom.codesmith.newir

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.bf.generated.DefaultProgNode
import com.github.xyzboom.codesmith.bf.generated.I_topDeclChild
import com.github.xyzboom.codesmith.newir.decl.IrClassDeclaration

class IrProgram(
    majorLanguage: Language = Language.KOTLIN
) : DefaultProgNode() {
    var majorLanguage = majorLanguage
        set(value) {
            for (declNode in topDeclChildren) {
                if (declNode is ILanguageTag) {
                    declNode.language = value
                }
            }
            field = value
        }

    val classes: List<IrClassDeclaration>
        get() {
            return topDeclChildren.map {
                it._topDeclChild.children.single() as I_topDeclChild
            }.filterIsInstance<IrClassDeclaration>()
        }
}