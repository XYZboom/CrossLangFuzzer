package com.github.xyzboom.codesmith.newir

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.bf.generated.DefaultProgNode
import com.github.xyzboom.codesmith.bf.generated.IChildOf_topDecl
import com.github.xyzboom.codesmith.newir.decl.IrClassDeclaration
import com.github.xyzboom.codesmith.newir.tags.ILanguageTag

class IrProgram(
    majorLanguage: Language = Language.KOTLIN
) : DefaultProgNode() {
    var majorLanguage = majorLanguage
        set(value) {
            for (declNode in topDeclChild) {
                if (declNode is ILanguageTag) {
                    declNode.language = value
                }
            }
            field = value
        }

    val classes: List<IrClassDeclaration>
        get() {
            return topDeclChild.map {
                it._topDeclChild.value as IChildOf_topDecl
            }.filterIsInstance<IrClassDeclaration>()
        }
}