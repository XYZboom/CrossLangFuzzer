package com.github.xyzboom.codesmith.printer

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.declarations.IrFile
import com.github.xyzboom.codesmith.ir.declarations.IrModule
import com.github.xyzboom.codesmith.ir.declarations.IrProgram
import com.github.xyzboom.codesmith.ir.visitor.IrTopDownVisitor
import kotlin.random.Random

class IrPrinterToSingleFile(
    private val filePrinters: List<IrPrinter<IrFile, String>>,
    private val random: Random = Random.Default,
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
        data.append(filePrinters.random(random).print(file))
        super.visitFile(file, data)
    }
}