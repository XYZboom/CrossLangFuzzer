package com.github.xyzboom.codesmith.newir.decl

import com.github.xyzboom.codesmith.Language.GROOVY4
import com.github.xyzboom.codesmith.bf.generated.DefaultClassNode
import com.github.xyzboom.codesmith.newir.ClassKind
import com.github.xyzboom.codesmith.newir.tags.IDeclNameTag
import com.github.xyzboom.codesmith.newir.tags.ILanguageTag
import com.github.xyzboom.codesmith.newir.types.IrTypeParameter
import io.github.xyzboom.bf.tree.NotNull

class IrClassDeclaration : DefaultClassNode(), ILanguageTag, IDeclNameTag {
    var classKind: ClassKind
        get() {
            return classKindChild.value as ClassKind
        }
        set(value) {
            classKindChild = NotNull(value)
        }

    @Suppress("UNCHECKED_CAST")
    val typeParameters: List<IrTypeParameter>
        get() = typeParamChild as List<IrTypeParameter>

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