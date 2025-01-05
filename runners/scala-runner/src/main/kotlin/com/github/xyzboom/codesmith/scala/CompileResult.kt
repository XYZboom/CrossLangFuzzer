package com.github.xyzboom.codesmith.scala

class CompileResult(
    val scalaResult: String?,
    val javaResult: String?,
    private val strictEquals: Boolean = false
) {
    val success: Boolean = scalaResult == null && javaResult == null
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CompileResult) return false
        if (strictEquals) {
            if (scalaResult == null && other.scalaResult != null) return false
            if (scalaResult != null && other.scalaResult == null) return false

            if (javaResult == null && other.javaResult != null) return false
            if (javaResult != null && other.javaResult == null) return false
        } else {
            if (success != other.success) return false
        }
        return true
    }

    override fun hashCode(): Int {
        if (strictEquals) {
            var result = if (scalaResult != null) 1 else 0
            result = 31 * result + if (javaResult != null) 1 else 0
            return result
        } else {
            return if (success) 1 else 0
        }
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