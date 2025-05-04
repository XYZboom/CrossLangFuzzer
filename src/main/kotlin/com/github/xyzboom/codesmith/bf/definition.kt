package com.github.xyzboom.codesmith.bf

import com.github.xyzboom.bf.def.DefinitionDecl
import com.github.xyzboom.bf.def.Parser
import com.github.xyzboom.codesmith.newir.IrProgram

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

const val extra = """
builtin:
  no-parent:
    - classKind
    - declName
    - lang
  no-cache:
    - classKind
    - lang
  impl-node:
    prog: com.github.xyzboom.codesmith.newir.IrProgram
"""

@DefinitionDecl(crossLangFuzzerDef, extraValue = extra)
const val crossLangFuzzerDef = """
// declaration
prog: topDecl+;
topDecl: _topDecl lang;
lang;
_topDecl: class | field | func;

class: classKind declName typeParam superType? superIntfList memberDecl+;
classKind;
superIntfList: superType*;

memberDecl: memberMethod; // others todo
memberMethod: declName param* returnType override*;

// override
override: memberMethod;

param: declName type;

returnType: type;
type: typeParam | superType;

superType: class typeArg*;
// leaf
declName;
typeParam;
typeArg;
field; // todo
func; // todo
"""
val definition by lazy {
    Parser().parseDefinition(
        crossLangFuzzerDef, "CrossLangFuzzer"
    )
}