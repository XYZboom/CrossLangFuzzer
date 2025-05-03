package com.github.xyzboom.codesmith.newir.decl

import com.github.xyzboom.codesmith.bf.generated.IDeclNameNode

@JvmInline
value class DeclName(val value: String): IDeclNameNode {
    override fun toString(): String {
        return value
    }
}