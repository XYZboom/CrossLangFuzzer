package com.github.xyzboom.codesmith.ir.generator

import com.github.xyzboom.codesmith.ir.declarations.IrModule

fun IrModule.dependsOn(module: IrModule) {
    this.dependencies.add(module)
}

fun IrModule.dependsOn(modules: List<IrModule>) {
    this.dependencies.addAll(modules)
}