package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

interface IrModule: IrElement {
    val name: String
    val dependencies: MutableList<IrModule>
    val packages: MutableList<IrPackage>
    val program: IrProgram
    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitModule(this, data)

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {
        packages.forEach { it.accept(visitor, data) }
    }

    fun dependsOn(module: IrModule) {
        dependencies.add(module)
    }

    fun dependsOn(modules: List<IrModule>) {
        dependencies.addAll(modules)
    }
}