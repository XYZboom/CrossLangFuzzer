package com.github.xyzboom.codesmith.newir.type

import com.github.xyzboom.codesmith.bf.generated.DefaultTypeParamNode
import com.github.xyzboom.codesmith.bf.generated.ITypeParamNameNode
import com.github.xyzboom.codesmith.newir.decl.DeclName
import com.github.xyzboom.codesmith.newir.tags.INameTag

class IrTypeParameter : DefaultTypeParamNode(), INameTag {
    override val name: String
        get() = (typeParamNameChild.value as Name).value

    @JvmInline
    value class Name(val value: String): ITypeParamNameNode {
        override fun toString(): String {
            return value
        }
    }
}