package com.github.xyzboom.codesmith.generator.impl

import com.github.xyzboom.codesmith.ir_old.container.IrContainer
import com.github.xyzboom.codesmith.ir_old.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir_old.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir_old.types.IrType

class SequentialTypeSelectionIrDeclGenerator(
    typeList: List<IrType>
) : IrDeclGeneratorImplOld() {
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