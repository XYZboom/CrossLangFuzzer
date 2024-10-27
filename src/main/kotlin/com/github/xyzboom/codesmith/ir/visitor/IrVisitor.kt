package com.github.xyzboom.codesmith.ir.visitor

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.declarations.*
import com.github.xyzboom.codesmith.ir.expressions.*

interface IrVisitor<out R, in D> {
    fun visitElement(element: IrElement, data: D): R

    fun visitDeclaration(declaration: IrDeclaration, data: D): R = visitElement(declaration, data)

    fun visitProgram(program: IrProgram, data: D): R = visitElement(program, data)

    fun visitModule(module: IrModule, data: D): R = visitElement(module, data)

    fun visitPackage(`package`: IrPackage, data: D): R = visitElement(`package`, data)

    fun visitFile(file: IrFile, data: D): R = visitElement(file, data)

    fun visitClass(clazz: IrClass, data: D): R = visitDeclaration(clazz, data)

    fun visitFunction(function: IrFunction, data: D): R = visitDeclaration(function, data)

    fun visitConstructor(constructor: IrConstructor, data: D): R = visitDeclaration(constructor, data)

    fun visitValueParameter(valueParameter: IrValueParameter, data: D): R = visitElement(valueParameter, data)

    fun visitCompanionObject(companionObject: IrCompanionObject, data: D): R = visitClass(companionObject, data)

    //<editor-fold desc="Expression">
    fun visitExpression(expression: IrExpression, data: D): R = visitElement(expression, data)

    fun visitConstantExpression(constantExpression: IrConstantExpression, data: D): R =
        visitExpression(constantExpression, data)

    fun visitFunctionCallExpression(functionCallExpression: IrFunctionCallExpression, data: D): R =
        visitExpression(functionCallExpression, data)

    fun visitConstructorCallExpression(constructorCallExpression: IrConstructorCallExpression, data: D): R =
        visitFunctionCallExpression(constructorCallExpression, data)

    fun visitAnonymousObject(anonymousObject: IrAnonymousObject, data: D): R =
        visitExpression(anonymousObject, data)

    fun visitTodoExpression(todoExpression: IrTodoExpression, data: D): R =
        visitExpression(todoExpression, data)
    //</editor-fold>
}