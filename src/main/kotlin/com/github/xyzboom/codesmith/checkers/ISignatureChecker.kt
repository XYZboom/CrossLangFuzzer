package com.github.xyzboom.codesmith.checkers

import com.github.xyzboom.codesmith.CodeSmithDsl
import com.github.xyzboom.codesmith.irOld.declarations.IrFunction
import com.github.xyzboom.codesmith.irOld.declarations.IrFunctionContainer

interface ISignatureChecker {
    /**
     * @return true if there is any function in this class has the same signature with [function]
     */
    @CodeSmithDsl
    fun IrFunctionContainer.containsSameSignature(function: IrFunction): Boolean
}