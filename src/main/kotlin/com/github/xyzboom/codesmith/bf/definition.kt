package com.github.xyzboom.codesmith.bf

import com.github.xyzboom.bf.def.Parser

enum class RefType {
    PROG,
    TOP_DECL,
    CLASS,

    CLASS_KIND
    ;

    companion object {
        private val uppercaseMap = buildMap {
            for (refType in RefType.entries) {
                put(refType.name.replace("_", ""), refType)
            }
        }

        @JvmStatic
        fun valueOfIgnoreCase(value: String): RefType? {
            return uppercaseMap[value.uppercase()]
        }
    }
}

val definition by lazy {
    Parser().parseDefinition(
        """
    // declaration
    prog: topDecl+;
    topDecl: _topDecl lang;
    lang;
    _topDecl: class | field | func;
    
    class: classKind declName typeParam superType? superIntfList memberDecl+;
    classKind;
    superIntfList: superType*;
    
    memberDecl: memberMethod; // others todo
    memberMethod: declName param* type override*;
    
    // override
    override: memberMethod;
    
    param: declName type;
    
    type: typeParam | superType;
    
    superType: class typeArg*;
    // leaf
    declName;
    typeParam;
    typeArg;
    field; // todo
    func; // todo
""".trimIndent(), "CrossLangFuzzer"
    )
}