package com.github.xyzboom.codesmith.ir

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.types.IrClassifier

val IrProgram.topologicalOrderedClasses: Sequence<IrClassDeclaration>
    get() {
        return sequence {
            val handled = mutableListOf<IrClassDeclaration>()
            while (handled.size < classes.size) {
                for (clazz in classes) {
                    val superType = clazz.superType
                    val allSuper = if (superType != null) {
                        clazz.implementedTypes + clazz.superType
                    } else clazz.implementedTypes
                    if (allSuper.asSequence().filterIsInstance<IrClassifier>().all { it.classDecl in handled }) {
                        yield(clazz)
                        handled.add(clazz)
                    }
                }
            }
        }
    }