package com.github.xyzboom.codesmith.mutator

import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import kotlin.random.Random

inline fun IrProgram.randomTraverseClasses(random: Random, visitor: (IrClassDeclaration) -> Boolean) {
    for (clazz in classes.shuffled(random)) {
        if (visitor.invoke(clazz)) {
            break
        }
    }
}

inline fun IrProgram.randomTraverseMemberFunctions(random: Random, visitor: (IrFunctionDeclaration) -> Boolean) {
    randomTraverseClasses(random) {
        for (func in it.functions) {
            if (visitor.invoke(func)) {
                return@randomTraverseClasses true
            }
        }
        false
    }
}
