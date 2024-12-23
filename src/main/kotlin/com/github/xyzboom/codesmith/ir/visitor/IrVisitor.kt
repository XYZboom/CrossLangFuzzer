package com.github.xyzboom.codesmith.ir.visitor

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.IrParameterList
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrPropertyDeclaration
import com.github.xyzboom.codesmith.ir.expressions.*

interface IrVisitor<out R, in D> {
    fun visitElement(element: IrElement, data: D): R

    fun visitDeclaration(declaration: IrDeclaration, data: D): R =
        visitElement(declaration, data)

    fun visitClass(clazz: IrClassDeclaration, data: D): R =
        visitDeclaration(clazz, data)

    fun visitFunction(function: IrFunctionDeclaration, data: D): R =
        visitDeclaration(function, data)

    fun visitProperty(property: IrPropertyDeclaration, data: D): R =
        visitDeclaration(property, data)

    fun visitExpression(expression: IrExpression, data: D): R =
        visitElement(expression, data)

    fun visitBlock(block: IrBlock, data: D): R =
        visitElement(block, data)

    fun visitParameterList(parameterList: IrParameterList, data: D): R =
        visitElement(parameterList, data)

    fun visitDefaultImplExpression(defaultImpl: IrDefaultImpl, data: D): R =
        visitExpression(defaultImpl, data)

    fun visitReturnExpression(returnExpression: IrReturnExpression, data: D): R =
        visitExpression(returnExpression, data)

    fun visitNewExpression(newExpression: IrNew, data: D): R =
        visitExpression(newExpression, data)

    fun visitFunctionCallExpression(functionCall: IrFunctionCall, data: D): R =
        visitExpression(functionCall, data)
}