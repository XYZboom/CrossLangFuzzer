package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.ir.IrAccessModifier
import com.github.xyzboom.codesmith.ir.declarations.*
import com.github.xyzboom.codesmith.ir.declarations.impl.*
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrConcreteType
import com.github.xyzboom.codesmith.ir.types.IrType

interface IrGenerator {
    fun generate(): IrProgram

    fun randomName(startsWithUpper: Boolean): String

    val IrModule.accessibleClasses: List<IrClass>

    @IrGeneratorDsl
    fun program(programCtx: IrProgram.() -> Unit = {}): IrProgram {
        return IrProgramImpl().apply(programCtx)
    }

    @IrGeneratorDsl
    fun IrProgram.module(name: String = randomName(false), moduleCtx: IrModule.() -> Unit = {}): IrModule {
        // apply moduleCtx first, so we can add dependencies of this@module.modules here.
        return IrModuleImpl(name, this).apply(moduleCtx).apply { this@module.modules.add(this) }
    }

    @IrGeneratorDsl
    fun IrModule.`package`(
        name: String = randomName(false),
        parent: IrPackage? = null,
        moduleCtx: IrPackage.() -> Unit = {}
    ): IrPackage {
        return IrPackageImpl(name, parent, this)
            .apply(moduleCtx).apply { this@`package`.packages.add(this) }
    }

    @IrGeneratorDsl
    fun IrPackage.file(name: String = randomName(false), fileCtx: IrFile.() -> Unit = {}): IrFile {
        return IrFileImpl(name, this).apply(fileCtx).apply { this@file.files.add(this) }
    }

    @IrGeneratorDsl
    fun IrFunctionContainer.function(
        name: String = randomName(false),
        accessModifier: IrAccessModifier = IrAccessModifier.PUBLIC,
        returnType: IrType,
        functionCtx: IrFunctionContainer.() -> Unit = {}
    ): IrFunction {
        return IrFunctionImpl(name, this, accessModifier, returnType)
            .apply(functionCtx).apply { this@function.functions.add(this) }
    }

    @IrGeneratorDsl
    fun IrClassContainer.`class`(
        name: String = randomName(true),
        containingFile: IrFile,
        classType: IrClassType = IrClassType.FINAL,
        accessModifier: IrAccessModifier = IrAccessModifier.PUBLIC,
        superType: IrConcreteType? = null,
        implementedTypes: List<IrConcreteType> = emptyList(),
        functionCtx: IrClassContainer.() -> Unit = {}
    ): IrClass {
        return IrClassImpl(name, containingFile, accessModifier, classType, superType, implementedTypes)
            .apply(functionCtx).apply { this@`class`.classes.add(this) }
    }

    fun IrProgram.generateModuleDependencies()

    fun IrPackage.generateFiles()

    fun IrFile.generateClasses()
}