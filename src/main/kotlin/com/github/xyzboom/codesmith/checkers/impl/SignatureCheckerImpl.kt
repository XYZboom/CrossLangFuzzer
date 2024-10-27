package com.github.xyzboom.codesmith.checkers.impl

import com.github.xyzboom.codesmith.CodeSmithDsl
import com.github.xyzboom.codesmith.checkers.ISignatureChecker
import com.github.xyzboom.codesmith.ir.declarations.IrFunction
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionContainer

class SignatureCheckerImpl: ISignatureChecker {
    @CodeSmithDsl
    override fun IrFunctionContainer.containsSameSignature(function: IrFunction): Boolean {
        return functions.any { it.isSameSignature(function) }
    }
}