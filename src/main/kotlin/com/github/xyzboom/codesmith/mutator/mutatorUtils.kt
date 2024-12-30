package com.github.xyzboom.codesmith.mutator

import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.irOld.declarations.IrClass
import com.github.xyzboom.codesmith.irOld.declarations.IrConstructor
import com.github.xyzboom.codesmith.irOld.declarations.IrFunction
import kotlin.random.Random
import com.github.xyzboom.codesmith.irOld.declarations.IrProgram as IrProgramOld

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

inline fun IrProgramOld.randomTraverseClasses(random: Random, visitor: (IrClass) -> Boolean) {
    outer@ for (module in modules.shuffled(random)) {
        for (`package` in module.packages.shuffled(random)) {
            for (file in `package`.files.shuffled(random)) {
                for (clazz in file.declarations.shuffled(random).filterIsInstance<IrClass>()) {
                    if (visitor.invoke(clazz)) {
                        break@outer
                    }
                }
            }
        }
    }
}

inline fun IrProgramOld.randomTraverseFunctions(random: Random, visitor: (IrFunction) -> Boolean) {
    randomTraverseClasses(random) { clazz ->
        for (function in clazz.functions) {
            if (visitor.invoke(function)) {
                return@randomTraverseClasses true
            }
        }
        false
    }
}

inline fun IrProgramOld.randomTraverseConstructors(random: Random, visitor: (IrConstructor) -> Boolean) {
    randomTraverseFunctions(random) { function ->
        if (function is IrConstructor) {
            if (visitor.invoke(function)) {
                return@randomTraverseFunctions true
            }
        }
        false
    }
}