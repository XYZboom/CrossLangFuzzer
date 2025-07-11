package com.github.xyzboom.codesmith.ir_old.container

import com.fasterxml.jackson.annotation.*
import com.github.xyzboom.codesmith.ir_old.IrProgram
import com.github.xyzboom.codesmith.ir_old.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir_old.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir_old.declarations.IrPropertyDeclaration
import com.github.xyzboom.codesmith.ir_old.types.IrClassifier

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator::class)
interface IrContainer {
    val classes: MutableList<IrClassDeclaration>
    val functions: MutableList<IrFunctionDeclaration>
    val properties: MutableList<IrPropertyDeclaration>

    var superContainer: IrContainer?

    @get:JsonIgnore
    val allClasses: List<IrClassDeclaration>
        get() = classes + (superContainer?.classes ?: emptyList())

    @get:JsonBackReference
    val program: IrProgram
        get() {
            if (this is IrProgram) {
                return this
            }
            if (superContainer is IrProgram) {
                return superContainer as IrProgram
            }
            return superContainer!!.program
        }

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