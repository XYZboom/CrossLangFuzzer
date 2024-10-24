package com.github.xyzboom.codesmith.checkers

import com.github.xyzboom.codesmith.CodeSmithDsl
import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.declarations.IrFunction

interface ISignatureChecker {
    /**
     * @return true if there is any function in this class has the same signature with [function]
     */
    @CodeSmithDsl
    fun IrClass.containsSameSignature(function: IrFunction): Boolean
}