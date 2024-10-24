package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.CodeSmithDsl
import com.github.xyzboom.codesmith.ir.IrAccessModifier
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.declarations.*
import com.github.xyzboom.codesmith.ir.declarations.impl.*
import com.github.xyzboom.codesmith.ir.expressions.IrConstructorCallExpression
import com.github.xyzboom.codesmith.ir.expressions.IrExpression
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrConcreteType
import com.github.xyzboom.codesmith.ir.types.IrFileType
import com.github.xyzboom.codesmith.ir.types.IrType

interface IrGenerator {
    fun generate(): IrProgram

    fun randomName(startsWithUpper: Boolean): String

    @CodeSmithDsl
    fun IrElement.generateValueArgumentFor(valueParameter: IrValueParameter): IrExpression

    @CodeSmithDsl
    fun program(programCtx: IrProgram.() -> Unit = {}): IrProgram {
        return IrProgramImpl().apply(programCtx)
    }

    @CodeSmithDsl
    fun IrProgram.module(name: String = randomName(false), moduleCtx: IrModule.() -> Unit = {}): IrModule {
        // apply moduleCtx first, so we can add dependencies of this@module.modules here.
        return IrModuleImpl(name, this).apply(moduleCtx).apply { this@module.modules.add(this) }
    }

    @CodeSmithDsl
    fun IrModule.`package`(
        name: String = randomName(false),
        parent: IrPackage? = null,
        moduleCtx: IrPackage.() -> Unit = {}
    ): IrPackage {
        return IrPackageImpl(name, parent, this)
            .apply(moduleCtx).apply { this@`package`.packages.add(this) }
    }

    @CodeSmithDsl
    fun IrPackage.file(
        name: String = randomName(false),
        fileType: IrFileType,
        fileCtx: IrFile.() -> Unit = {}
    ): IrFile {
        return IrFileImpl(name, this, fileType)
            .apply(fileCtx).apply { this@file.files.add(this) }
    }

    @CodeSmithDsl
    fun IrFunctionContainer.function(
        name: String = randomName(false),
        accessModifier: IrAccessModifier = IrAccessModifier.PUBLIC,
        valueParameters: MutableList<IrValueParameter> = mutableListOf(),
        returnType: IrType,
        functionCtx: IrFunctionContainer.() -> Unit = {}
    ): IrFunction {
        return IrFunctionImpl(name, this, accessModifier, valueParameters, returnType)
            .apply(functionCtx).apply { this@function.functions.add(this) }
    }

    @CodeSmithDsl
    fun IrClassContainer.`class`(
        name: String = randomName(true),
        containingFile: IrFile,
        classType: IrClassType = IrClassType.FINAL,
        accessModifier: IrAccessModifier = IrAccessModifier.PUBLIC,
        superType: IrConcreteType? = null,
        implementedTypes: List<IrConcreteType> = emptyList(),
        classCtx: IrClass.() -> Unit = {}
    ): IrClass {
        return IrClassImpl(name, containingFile, accessModifier, classType, superType, implementedTypes)
            .apply(classCtx).apply { this@`class`.classes.add(this) }
    }

    @CodeSmithDsl
    fun IrClass.constructor(
        superCall: IrConstructorCallExpression,
        accessModifier: IrAccessModifier = IrAccessModifier.PUBLIC,
        valueParameters: MutableList<IrValueParameter> = mutableListOf(),
        constructorCtx: IrConstructor.() -> Unit = {}
    ): IrConstructor

    fun IrProgram.generateModuleDependencies()

    fun IrPackage.generateFiles()

    fun IrFile.generateClasses()

    fun IrClass.generateConstructors(num: Int)

    fun IrFunction.randomValueParameter(): IrValueParameter
}