package com.github.xyzboom.codesmith.newir

import com.github.xyzboom.bf.tree.INode
import com.github.xyzboom.codesmith.bf.generated.IClassKindNode

enum class ClassKind: IClassKindNode {
    ABSTRACT,
    INTERFACE,
    OPEN,
    FINAL;

    override var parent: INode
        get() = throw UnsupportedOperationException()
        set(_) = throw UnsupportedOperationException()
}