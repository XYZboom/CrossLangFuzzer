package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter

class SequentialTypeSelectionIrDeclGenerator(
    typeList: List<IrType>
) : IrDeclGenerator() {
    private val iterator = typeList.iterator()
    override fun randomType(
        fromClasses: List<IrClassDeclaration>,
        typeParameterFromClass: List<IrTypeParameter>?,
        typeParameterFromFunction: List<IrTypeParameter>?,
        finishTypeArguments: Boolean,
        filter: (IrType) -> Boolean
    ): IrType? {
        return if (iterator.hasNext()) {
            iterator.next()
        } else null
    }
}