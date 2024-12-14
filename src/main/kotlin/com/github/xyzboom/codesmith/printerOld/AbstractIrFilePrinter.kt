package com.github.xyzboom.codesmith.printerOld

import com.github.xyzboom.codesmith.irOld.declarations.IrFile
import com.github.xyzboom.codesmith.irOld.visitor.IrTopDownVisitor

abstract class AbstractIrFilePrinter: IrTopDownVisitor<StringBuilder>, IrPrinter<IrFile, String> {
}