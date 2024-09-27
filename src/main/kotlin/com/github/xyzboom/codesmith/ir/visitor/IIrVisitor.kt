package com.github.xyzboom.codesmith.ir.visitor

import com.github.xyzboom.codesmith.ir.IIrElement
import com.github.xyzboom.codesmith.ir.declarations.IrFile
import com.github.xyzboom.codesmith.ir.declarations.IrModule
import com.github.xyzboom.codesmith.ir.declarations.IrProgram

interface IIrVisitor<out R, in D> {
    fun visitElement(element: IIrElement, data: D): R

    fun visitModule(module: IrModule, data: D): R = visitElement(module, data)

    fun visitProgram(program: IrProgram, data: D): R = visitElement(program, data)

    fun visitFile(file: IrFile, data: D): R = visitElement(file, data)
}