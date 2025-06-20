package com.github.xyzboom.codesmith.newir.types

import com.github.xyzboom.codesmith.bf.generated.ISuperTypeNode
import com.github.xyzboom.codesmith.bf.generated.ITypeNode
import com.github.xyzboom.codesmith.bf.generated.ITypeNode0
import com.github.xyzboom.codesmith.bf.generated.ITypeNode1
import com.github.xyzboom.codesmith.bf.generated.ITypeParamNode
import com.github.xyzboom.codesmith.newir.ClassKind
import io.github.xyzboom.bf.tree.INode
import io.github.xyzboom.bf.tree.NotNull

class IrType: ITypeNode0, ITypeNode1 {
    override val children: MutableList<INode> = mutableListOf()
    override lateinit var typeParamChild: NotNull<ITypeParamNode>
    override lateinit var superTypeChild: NotNull<ISuperTypeNode>

    override fun addChild(node: INode) {
        TODO("Not yet implemented")
    }

    override lateinit var parent: INode

    val classKind: ClassKind
}