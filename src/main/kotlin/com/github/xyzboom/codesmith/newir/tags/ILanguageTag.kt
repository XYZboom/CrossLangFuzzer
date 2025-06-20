package com.github.xyzboom.codesmith.newir.tags

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.bf.generated.IChildOf_topDecl
import io.github.xyzboom.bf.tree.NotNull

interface ILanguageTag: IChildOf_topDecl {
    var language: Language
        get()  = _topDeclParent.topDeclParent.langChild.value as Language
        set(value) {
            _topDeclParent.topDeclParent.langChild = NotNull(value)
        }
}