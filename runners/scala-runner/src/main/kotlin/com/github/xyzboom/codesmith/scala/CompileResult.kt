package com.github.xyzboom.codesmith.scala

class CompileResult(
    val scalaResult: String?,
    val javaResult: String?
) {
    val success: Boolean = scalaResult == null && javaResult == null
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CompileResult) return false

        if (scalaResult == null && other.scalaResult != null) return false
        if (scalaResult != null && other.scalaResult == null) return false

        if (javaResult == null && other.javaResult != null) return false
        if (javaResult != null && other.javaResult == null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = if (scalaResult != null) 1 else 0
        result = 31 * result + if (javaResult != null) 1 else 0
        return result
    }

    operator fun component1(): String? {
        return scalaResult
    }

    operator fun component2(): String? {
        return javaResult
    }

    override fun toString(): String {
        return if (scalaResult != null) {
            "Scala Error:\n$scalaResult"
        } else {
            "Java Error:\n$javaResult"
        }
    }
}