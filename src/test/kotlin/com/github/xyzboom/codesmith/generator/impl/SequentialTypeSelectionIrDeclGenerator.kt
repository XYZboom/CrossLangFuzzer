package com.github.xyzboom.codesmith.generator.impl

import com.github.xyzboom.codesmith.ir.container.IrContainer
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.types.IrType

class SequentialTypeSelectionIrDeclGenerator(
    typeList: List<IrType>
) : IrDeclGeneratorImpl() {
    private val iterator = typeList.iterator()
    override fun randomType(
        from: IrContainer,
        classContext: IrClassDeclaration?,
        functionContext: IrFunctionDeclaration?,
        finishTypeArguments: Boolean,
        filter: (IrType) -> Boolean
    ): IrType? {
        return if (iterator.hasNext()) {
            iterator.next()
        } else null
    }
}