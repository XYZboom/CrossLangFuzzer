package com.github.xyzboom.codesmith.ir

fun IrProgram.setMajorLanguage(language: Language) {
    for (clazz in classes) {
        clazz.language = language
    }
    for (func in functions) {
        func.language = language
    }
}