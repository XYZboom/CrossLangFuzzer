package com.github.xyzboom.codesmith.ir_old.declarations

import com.fasterxml.jackson.annotation.JsonTypeName
import com.github.xyzboom.codesmith.LanguageOld
import com.github.xyzboom.codesmith.ir_old.IrProgram
import com.github.xyzboom.codesmith.ir_old.container.IrContainer
import com.github.xyzboom.codesmith.ir_old.types.IrType
import com.github.xyzboom.codesmith.ir_old.types.builtin.IrUnit
import com.github.xyzboom.codesmith.ir_old.visitor.IrVisitor

@JsonTypeName("property")
class IrPropertyDeclaration(
    name: String,
    var container: IrContainer
) : IrDeclaration(name), IrClassMember {

    /**
     * only available when [language] is [LanguageOld.JAVA]
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