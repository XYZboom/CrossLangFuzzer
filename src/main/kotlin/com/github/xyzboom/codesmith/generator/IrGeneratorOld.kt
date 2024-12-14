package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.CodeSmithDsl
import com.github.xyzboom.codesmith.irOld.IrAccessModifier
import com.github.xyzboom.codesmith.irOld.IrElement
import com.github.xyzboom.codesmith.irOld.declarations.*
import com.github.xyzboom.codesmith.irOld.declarations.impl.*
import com.github.xyzboom.codesmith.irOld.expressions.IrConstructorCallExpression
import com.github.xyzboom.codesmith.irOld.expressions.IrExpression
import com.github.xyzboom.codesmith.irOld.expressions.IrFunctionCallExpression
import com.github.xyzboom.codesmith.irOld.types.IrClassType
import com.github.xyzboom.codesmith.irOld.types.IrConcreteType
import com.github.xyzboom.codesmith.irOld.types.IrFileType

interface IrGeneratorOld {
    fun generate(): IrProgram

    fun randomName(startsWithUpper: Boolean): String

    @CodeSmithDsl
    fun IrElement.generateExpressionFor(clazz: IrClass): IrExpression

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
        returnType: IrConcreteType,
        functionCtx: IrFunction.() -> Unit = {}
    ): IrFunction?

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
    ): IrConstructor?

    fun IrProgram.generateModuleDependencies()

    fun generateClassInheritance()

    fun topologyVisit(visitor: IrClass.() -> Unit)

    fun IrPackage.generateFiles()

    fun IrFile.generateClasses()

    fun IrClass.generateConstructors(num: Int, accessModifier: IrAccessModifier? = null): List<IrConstructor>

    /**
     *
     * @param superConstructor
     * @param accessModifier specify [accessModifier] wanted
     * @return null if class has a constructor whose signature is the same as generated constructor
     */
    fun IrClass.generateConstructor(
        superConstructor: IrConstructor, accessModifier: IrAccessModifier? = null
    ): IrConstructor?

    fun IrClass.generateFunction(): IrFunction?

    fun IrFunction.generateExpressions()

    fun generateFunctionCallExpr(function: IrFunction): IrFunctionCallExpression

    fun IrFunction.randomValueParameter(): IrValueParameter
}