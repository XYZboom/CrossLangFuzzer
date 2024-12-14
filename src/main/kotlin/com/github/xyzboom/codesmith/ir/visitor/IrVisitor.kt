package com.github.xyzboom.codesmith.ir.visitor

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration

interface IrVisitor<out R, in D> {
    fun visitElement(element: IrElement, data: D): R

    fun visitDeclaration(declaration: IrDeclaration, data: D): R =
        visitElement(declaration, data)

    fun visitClass(clazz: IrClassDeclaration, data: D): R =
        visitDeclaration(clazz, data)

    fun visitFunction(function: IrFunctionDeclaration, data: D): R =
        visitDeclaration(function, data)
}