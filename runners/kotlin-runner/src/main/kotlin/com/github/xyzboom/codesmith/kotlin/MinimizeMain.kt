package com.github.xyzboom.codesmith.kotlin

import com.github.ajalt.clikt.parsers.CommandLineParser
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.serde.gson
import com.github.xyzboom.codesmith.minimize.MinimizeRunnerImpl
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import java.io.File

object MinimizeMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val prog = gson.fromJson(
            File("/root/autodl-tmp/Code/CodeSmith/out/00000197d46cda3e/main.json").readText(),
            IrProgram::class.java
        )
        val runner = CrossLangFuzzerKotlinRunner()
        CommandLineParser.parseAndRun(runner, listOf("--dt=false")) {
            it as CrossLangFuzzerKotlinRunner
            val prog = MinimizeRunnerImpl(it).minimize(
                prog, it.compile(prog)
            )
            val fileContent = IrProgramPrinter().printToSingle(prog)
            File("./out/main-min1.kt").writeText(fileContent)
        }
    }
}