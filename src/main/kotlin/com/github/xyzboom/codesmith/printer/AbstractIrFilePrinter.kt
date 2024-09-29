package com.github.xyzboom.codesmith.printer

import com.github.xyzboom.codesmith.ir.declarations.IrFile
import com.github.xyzboom.codesmith.ir.visitor.IrTopDownVisitor

abstract class AbstractIrFilePrinter: IrTopDownVisitor<StringBuilder>, IrPrinter<IrFile, String> {
}