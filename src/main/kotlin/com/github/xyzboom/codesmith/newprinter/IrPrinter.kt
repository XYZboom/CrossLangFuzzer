package com.github.xyzboom.codesmith.newprinter

import io.github.xyzboom.bf.tree.INode

interface IrPrinter<in IR: INode, out R> {
    fun print(element: IR): R
}