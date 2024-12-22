package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.container.IrContainer
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.builtin.IrUnit
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

class IrPropertyDeclaration(
    name: String,
    var container: IrContainer
) : IrDeclaration(name), IrClassMember {

    /**
     * only available when [language] is [Language.JAVA]
     */
    var printNullableAnnotations: Boolean = false
    var isOverride: Boolean = false
    var isOverrideStub: Boolean = false
    var override = mutableListOf<IrPropertyDeclaration>()
    var isFinal = false
    val topLevel: Boolean get() = container is IrProgram
    var type: IrType = IrUnit
    var readonly: Boolean = false

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R {
        return visitor.visitProperty(this, data)
    }

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {

    }
}