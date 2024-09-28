package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.ir.declarations.*
import com.github.xyzboom.codesmith.ir.declarations.impl.IrFileImpl
import com.github.xyzboom.codesmith.ir.declarations.impl.IrFunctionImpl
import com.github.xyzboom.codesmith.ir.declarations.impl.IrModuleImpl
import com.github.xyzboom.codesmith.ir.declarations.impl.IrProgramImpl

interface IrGenerator {
    fun generate(): IrProgram

    fun randomName(startsWithUpper: Boolean): String

    @IrGeneratorDsl
    fun program(programCtx: IrProgram.() -> Unit = {}): IrProgram {
        return IrProgramImpl().apply(programCtx)
    }

    @IrGeneratorDsl
    fun IrProgram.module(name: String = randomName(false), moduleCtx: IrModule.() -> Unit = {}): IrModule {
        // apply moduleCtx first, so we can add dependencies of this@module.modules here.
        return IrModuleImpl(name).apply(moduleCtx).apply { this@module.modules.add(this) }
    }

    @IrGeneratorDsl
    fun IrModule.file(name: String = randomName(false), fileCtx: IrFile.() -> Unit = {}): IrFile {
        return IrFileImpl(name, this).apply(fileCtx).apply { this@file.files.add(this) }
    }

    @IrGeneratorDsl
    fun IrFunctionContainer.function(
        name: String = randomName(false),
        functionCtx: IrFunctionContainer.() -> Unit = {}
    ): IrFunction {
        return IrFunctionImpl(name, this).apply(functionCtx)
    }

    fun IrProgram.generateModuleDependencies()
}