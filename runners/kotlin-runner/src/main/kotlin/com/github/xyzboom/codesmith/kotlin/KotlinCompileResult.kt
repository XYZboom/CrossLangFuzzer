package com.github.xyzboom.codesmith.kotlin

import com.github.xyzboom.codesmith.CompileResult
import org.jetbrains.kotlin.test.JavaCompilationError

fun toCompileResult(
    version: String,
    e: Throwable?,
    strict: Boolean = false
): CompileResult {
    if (e is JavaCompilationError) {
        return CompileResult(version, null, e.stackTraceToString(), strict)
    }
    return CompileResult(version, e?.stackTraceToString(), null, strict)
}