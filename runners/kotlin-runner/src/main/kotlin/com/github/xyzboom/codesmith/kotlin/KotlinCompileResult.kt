package com.github.xyzboom.codesmith.kotlin

import org.jetbrains.kotlin.test.JavaCompilationError

class KotlinCompileResult(
    val e: Throwable?,
    val fileContent: String,
    val testName: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KotlinCompileResult) return false

        if (e == null && other.e != null) return false
        if (e != null && other.e == null) return false
         if (e is JavaCompilationError) return other.e is JavaCompilationError
        if (fileContent != other.fileContent) return false

        return true
    }

    override fun hashCode(): Int {
        var result = if (e == null) {
            0
        } else {
            1
        }
        result = 31 * result + fileContent.hashCode()
        return result
    }
}