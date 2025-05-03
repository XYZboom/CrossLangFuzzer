package com.github.xyzboom.codesmith

import com.github.xyzboom.codesmith.bf.generated.ILangNode

enum class Language(
    val extension: String
): ILangNode {
    KOTLIN("kt"),
    JAVA("java"),
    SCALA("scala"),
    GROOVY4("groovy"),
    GROOVY5("groovy");
}