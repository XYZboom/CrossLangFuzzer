package com.github.xyzboom.codesmith.newir.decl

import io.github.xyzboom.bf.tree.ITreeChild
import io.github.xyzboom.bf.tree.ITreeParent
import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.Language.GROOVY4
import com.github.xyzboom.codesmith.Language.JAVA
import com.github.xyzboom.codesmith.bf.generated.DefaultClassNode
import com.github.xyzboom.codesmith.bf.generated.ITopDeclNode
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.newir.ClassKind
import com.github.xyzboom.codesmith.newir.ILanguageTag
import com.github.xyzboom.codesmith.newir.type.IrTypeParameter

class IrClassDeclaration : DefaultClassNode(), ILanguageTag {
    override var language: Language
        get() {
            val parent2 = (parent as ITreeChild).parent as ITopDeclNode
            return parent2.langChild as Language
        }
        set(value) {
            val parent2 = (parent as ITreeChild).parent as ITopDeclNode
            parent2.langChild = value
        }

    var classKind: ClassKind
        get() {
            return classKindChild as ClassKind
        }
        set(value) {
            classKindChild = value
        }

    val name: String
        get() = (declNameChild as DeclName).value

    @Suppress("UNCHECKED_CAST")
    val typeParameters: List<IrTypeParameter>
        get() = typeParamChildren as List<IrTypeParameter>

    fun changeLanguageIfNotSuitable() {
        if (language != GROOVY4) {
            return
        }
        TODO()
        // Default method in groovy4 interface is actual not default in java side.
        /*if (classKind == ClassKind.INTERFACE && functions.any { it.body != null }) {
            language = JAVA
        }*/
    }
}