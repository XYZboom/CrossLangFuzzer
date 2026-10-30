package com.github.xyzboom.codesmith.kotlin

import com.github.ajalt.clikt.parsers.CommandLineParser
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.github.xyzboom.codesmith.serde.gson
import java.io.File

object MinimizeMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val runner = CrossLangFuzzerKotlinRunner()
        val program = gson.fromJson(
            File("/home/xyzboom/Code/kotlin/CrossLangFuzzer/out/raw/00000198fdb7d7e5/main-min.json").reader(),
            IrProgram::class.java,
        )
        println(program)
        println(IrProgramPrinter().printToSingle(program))
        CommandLineParser.parseAndRun(runner, emptyList()) { }
    }
}
