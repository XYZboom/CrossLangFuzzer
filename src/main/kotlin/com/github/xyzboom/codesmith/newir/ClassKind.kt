package com.github.xyzboom.codesmith.newir

import com.github.xyzboom.codesmith.bf.generated.IClassKindNode

enum class ClassKind: IClassKindNode {
    ABSTRACT,
    INTERFACE,
    OPEN,
    FINAL;
}