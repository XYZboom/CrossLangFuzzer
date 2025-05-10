package com.github.xyzboom.codesmith.newir.decl

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.bf.generated.DefaultMemberMethodNode
import com.github.xyzboom.codesmith.newir.tags.ILanguageTag
import com.github.xyzboom.codesmith.newir.tags.INameTag

class IrMemberMethod : DefaultMemberMethodNode(), ILanguageTag, INameTag {
    override var language: Language
        get() = TODO("Not yet implemented")
        set(value) {}
    override val name: String
        get() = TODO("Not yet implemented")

}