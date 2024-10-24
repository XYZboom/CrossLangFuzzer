package com.github.xyzboom.codesmith.checkers.impl

import com.github.xyzboom.codesmith.CodeSmithDsl
import com.github.xyzboom.codesmith.checkers.ISignatureChecker
import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.declarations.IrFunction

class SignatureCheckerImpl: ISignatureChecker {
    @CodeSmithDsl
    override fun IrClass.containsSameSignature(function: IrFunction): Boolean {
        return declarations.filterIsInstance<IrFunction>().any { it.isSameSignature(function) }
    }
}