package com.github.xyzboom.codesmith.mutator

import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.declarations.IrConstructor
import com.github.xyzboom.codesmith.ir.declarations.IrFunction
import com.github.xyzboom.codesmith.ir.declarations.IrProgram
import kotlin.random.Random

inline fun IrProgram.randomTraverseClasses(random: Random, visitor: (IrClass) -> Boolean) {
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

inline fun IrProgram.randomTraverseFunctions(random: Random, visitor: (IrFunction) -> Boolean) {
    randomTraverseClasses(random) { clazz ->
        for (function in clazz.functions) {
            if (visitor.invoke(function)) {
                return@randomTraverseClasses true
            }
        }
        false
    }
}

inline fun IrProgram.randomTraverseConstructors(random: Random, visitor: (IrConstructor) -> Boolean) {
    randomTraverseFunctions(random) { function ->
        if (function is IrConstructor) {
            if (visitor.invoke(function)) {
                return@randomTraverseFunctions true
            }
        }
        false
    }
}