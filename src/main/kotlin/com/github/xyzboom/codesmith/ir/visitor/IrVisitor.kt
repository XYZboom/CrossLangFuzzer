package com.github.xyzboom.codesmith.ir.visitor

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.declarations.*

interface IrVisitor<out R, in D> {
    fun visitElement(element: IrElement, data: D): R

    fun visitDeclaration(declaration: IrDeclaration, data: D): R = visitElement(declaration, data)

    fun visitProgram(program: IrProgram, data: D): R = visitDeclaration(program, data)

    fun visitModule(module: IrModule, data: D): R = visitDeclaration(module, data)

    fun visitPackage(`package`: IrPackage, data: D): R = visitDeclaration(`package`, data)

    fun visitFile(file: IrFile, data: D): R = visitDeclaration(file, data)

    fun visitClass(clazz: IrClass, data: D): R = visitDeclaration(clazz, data)

    fun visitFunction(function: IrFunction, data: D): R = visitDeclaration(function, data)
}