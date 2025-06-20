package com.github.xyzboom.codesmith.bf

import com.github.xyzboom.codesmith.newir.IrProgram
import com.github.xyzboom.codesmith.newir.decl.IrClassDeclaration
import com.github.xyzboom.codesmith.newir.types.IrType
import com.github.xyzboom.codesmith.newir.types.IrTypeParameter
import io.github.xyzboom.bf.def.DefExtra
import io.github.xyzboom.bf.def.DefImplPair
import io.github.xyzboom.bf.def.DefinitionDecl
import io.github.xyzboom.bf.def.Parser

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

@DefinitionDecl(
    crossLangFuzzerDef,
    extra = DefExtra(
        noParentNames = [
            "classKind",
            "declName",
            "typeParamName",
            "lang",
        ],
        noCacheNames = [
            "classKind",
            "declName",
            "typeParamName",
            "lang",
        ],
        implNames = [
            DefImplPair("prog", IrProgram::class),
            DefImplPair("class", IrClassDeclaration::class),
            DefImplPair("type", IrType::class),
            DefImplPair("typeParam", IrTypeParameter::class),
        ]
    )
)
const val crossLangFuzzerDef = """
// declaration
prog: topDecl+;
topDecl: _topDecl lang;
lang;
_topDecl: class | field | func;

class: classKind declName typeParam* superType? superIntfList memberDecl+;
classKind;
superIntfList: superType*;

memberDecl: memberMethod; // others todo
memberMethod: declName param* type override*;

// override
override: memberMethod;

param: declName type;

type: typeParam | superType;
typeParam: typeParamName;

superType: class typeArg*;
typeArg: type;
// leaf
declName;
typeParamName;
field; // todo
func; // todo
"""
val definition by lazy {
    Parser().parseDefinition(
        crossLangFuzzerDef, "CrossLangFuzzer"
    )
}