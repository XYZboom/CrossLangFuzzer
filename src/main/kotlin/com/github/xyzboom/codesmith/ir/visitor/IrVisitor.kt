package com.github.xyzboom.codesmith.ir.visitor

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.declarations.*

interface IrVisitor<out R, in D> {
    fun visitElement(element: IrElement, data: D): R

    fun visitDeclaration(declaration: IrDeclaration, data: D): R = visitElement(declaration, data)

    fun visitProgram(program: IrProgram, data: D): R = visitElement(program, data)

    fun visitModule(module: IrModule, data: D): R = visitElement(module, data)

    fun visitPackage(`package`: IrPackage, data: D): R = visitElement(`package`, data)

    fun visitFile(file: IrFile, data: D): R = visitElement(file, data)

    fun visitClass(clazz: IrClass, data: D): R = visitDeclaration(clazz, data)

    fun visitFunction(function: IrFunction, data: D): R = visitDeclaration(function, data)

    fun visitValueParameter(valueParameter: IrValueParameter, data: D): R = visitElement(valueParameter, data)
}