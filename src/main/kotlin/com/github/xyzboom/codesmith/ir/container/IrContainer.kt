package com.github.xyzboom.codesmith.ir.container

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.types.IrClassifier

interface IrContainer {
    val classes: MutableList<IrClassDeclaration>
    val functions: MutableList<IrFunctionDeclaration>

    var superContainer: IrContainer?
    val allClasses: List<IrClassDeclaration>
        get() = classes + (superContainer?.classes ?: emptyList())

    fun traverseClassesTopologically(visitor: (IrClassDeclaration) -> Unit) {
        val visited = hashSetOf<IrClassDeclaration>()
        val ringRecord = hashSetOf<IrClassDeclaration>()
        val deque = ArrayDeque<IrClassDeclaration>()
        deque.addAll(classes)
        while (deque.isNotEmpty()) {
            val clazz = deque.removeFirst()
            val superType = clazz.superType
            val intf = clazz.implementedTypes
            if (superType == null && intf.isEmpty()) {
                visitor(clazz)
                visited.add(clazz)
                continue
            }
            val superAndIntf = if (superType != null) {
                intf + superType
            } else {
                intf
            }.mapNotNull { (it as? IrClassifier?)?.classDecl }
            if (visited.containsAll(superAndIntf.toSet())) {
                visitor(clazz)
                visited.add(clazz)
                continue
            }
            if (ringRecord.contains(clazz)) {
                throw IllegalStateException("Ring inheritance detected!")
            }
            ringRecord.add(clazz)
            deque.addLast(clazz)
        }
    }
}