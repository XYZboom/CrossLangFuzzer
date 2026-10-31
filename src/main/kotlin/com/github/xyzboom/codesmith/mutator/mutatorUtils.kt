package com.github.xyzboom.codesmith.mutator

import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.classes
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import kotlin.random.Random

inline fun IrProgram.randomTraverseClasses(
    random: Random, visitor: (IrClassDeclaration) -> Boolean
) {
    for (clazz in classes.shuffled(random)) {
        if (visitor.invoke(clazz)) {
            break
        }
    }
}


inline fun IrProgram.randomTraverseMemberFunctions(
    random: Random,
    // IrClassDeclaration is needed as new IrFunctionDeclaration does not hold a reference of its containing class.
    visitor: (IrFunctionDeclaration, IrClassDeclaration) -> Boolean
) {
    randomTraverseClasses(random) { clazz ->
        for (func in clazz.functions) {
            if (visitor.invoke(func, clazz)) {
                return@randomTraverseClasses true
            }
        }
        false
    }
}
