package com.github.xyzboom.codesmith.newir.type

import com.github.xyzboom.codesmith.bf.generated.DefaultTypeParamNode
import com.github.xyzboom.codesmith.bf.generated.ITypeParamNameNode
import com.github.xyzboom.codesmith.newir.INameTag
import com.github.xyzboom.codesmith.newir.decl.DeclName

class IrTypeParameter : DefaultTypeParamNode(), INameTag {
    override val name: String
        get() = (typeParamNameChild as Name).value

    @JvmInline
    value class Name(val value: String): ITypeParamNameNode {
        override fun toString(): String {
            return value
        }
    }
}