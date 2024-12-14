package com.github.xyzboom.codesmith.printerOld

import com.github.xyzboom.codesmith.irOld.declarations.IrFile
import com.github.xyzboom.codesmith.irOld.declarations.IrModule
import com.github.xyzboom.codesmith.irOld.declarations.IrProgram
import com.github.xyzboom.codesmith.irOld.types.IrFileType
import com.github.xyzboom.codesmith.irOld.visitor.IrTopDownVisitor

class IrPrinterToSingleFile(
    private val filePrinters: Map<IrFileType, IrPrinter<IrFile, String>>,
): IrTopDownVisitor<StringBuilder>, IrPrinter<IrProgram, String> {
    private val stringBuilder = StringBuilder()

    init {
        assert(filePrinters.isNotEmpty()) {
            "file printers must be specified!"
        }
    }

    override fun print(element: IrProgram): String {
        stringBuilder.clear()
        visitProgram(element, stringBuilder)
        return stringBuilder.toString()
    }

    override fun visitProgram(program: IrProgram, data: StringBuilder) {
        val processedModule = HashSet<IrModule>()
        val notProcessedModule = HashSet<IrModule>(program.modules)
        while (processedModule.size != program.modules.size) {
            val currentProcessedModule = HashSet<IrModule>()
            for (module in notProcessedModule) {
                if (module.dependencies.all { it in processedModule }) {
                    visitModule(module, data)
                    processedModule.add(module)
                    currentProcessedModule.add(module)
                }
            }
            notProcessedModule.removeAll(currentProcessedModule)
        }
    }

    override fun visitModule(module: IrModule, data: StringBuilder) {
        data.append("// MODULE: ${module.name}")
        if (module.dependencies.isNotEmpty()) {
            data.append("(${module.dependencies.joinToString(", ") { it.name }})")
        }
        data.append("\n")
        super.visitModule(module, data)
    }

    override fun visitFile(file: IrFile, data: StringBuilder) {
        val printer = (filePrinters[file.fileType]
            ?: throw NullPointerException("A printer of ${file.fileType} must be provided!"))
        data.append(printer.print(file))
        super.visitFile(file, data)
    }
}