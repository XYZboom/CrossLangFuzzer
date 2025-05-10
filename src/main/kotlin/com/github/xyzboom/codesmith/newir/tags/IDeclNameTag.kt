package com.github.xyzboom.codesmith.newir.tags

import com.github.xyzboom.codesmith.bf.generated.IParentOfDeclName
import com.github.xyzboom.codesmith.newir.decl.DeclName

interface IDeclNameTag: INameTag, IParentOfDeclName {
    override val name: String get() = (declNameChild.value as DeclName).value
}