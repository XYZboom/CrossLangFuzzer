package com.github.xyzboom.codesmith

import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.choice
import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.IrDeclGenerator
import com.github.xyzboom.codesmith.generator.impl.IrDeclGeneratorImpl
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.mutator.MutatorConfig
import com.github.xyzboom.codesmith.mutator.impl.IrMutatorImpl
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.github.xyzboom.codesmith.utils.mkdirsIfNotExists
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.measureTime


class CodeSmithGroovyRunner : CommonCompilerRunner() {

    private val groovyVersions by option("--gv")
        .choice(*GroovyCompilerWrapper.groovyJarsWithVersion.keys.toTypedArray())
        .split(",")
        .required()

    private val groovyCompilers: List<GroovyCompilerWrapper> by lazy {
        groovyVersions.map { GroovyCompilerWrapper(it) }
    }

    override fun run() {
        val doOneRoundFunction: () -> Unit
        if (differentialTesting) {
            if (groovyVersions.size <= 1) {
                System.err.println("You must provide at least 2 different versions of compiler to do differential testing!")
                exitProcess(-1)
            }
            doOneRoundFunction = ::doDifferentialTestingOneRound
        } else {
            doOneRoundFunction = ::doOneRound
        }
        println("start at: $tempDir")
        var i = 0
        while (true) {
            val dur = measureTime { doOneRoundFunction() }
            println("${i++}: $dur")
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun recordCompileResult(
        sourceSingleFileContent: String,
        compileResults: Map<String, CompileResult>
    ) {
        val dir = File(logFile, System.currentTimeMillis().toHexString()).mkdirsIfNotExists()
        File("codesmith-trace.log").copyTo(File(dir, "codesmith-trace.log"))
        for (compileResult in compileResults) {
            if (!compileResult.value.success) {
                File(dir, "groovy-${compileResult.key}-error.txt").writeText(compileResult.toString())
            }
        }
        File(dir, "main.groovy").writeText(sourceSingleFileContent)
    }

    private fun runDifferential(
        groovyCompilers: List<GroovyCompilerWrapper>,
        generator: IrDeclGenerator,
        printer: IrProgramPrinter,
        program: IrProgram,
        stopOnErrors: Boolean
    ) {
        val compileResults = groovyCompilers.associate { compiler ->
            if (compiler.isGroovy5) {
                program.majorLanguage = Language.GROOVY5
            } else {
                program.majorLanguage = Language.GROOVY4
            }
            // Due to the compatibility between Groovy syntax and Java,
            // it is reasonable to conduct differential testing directly using the 'shuffle language'
            generator.shuffleLanguage(program)
            compiler.version to compiler.compileGroovyWithJava(printer, program)
        }

        if (compileResults.values.toSet().size != 1) {
            recordCompileResult(printer.printToSingle(program), compileResults)
            if (stopOnErrors) {
                exitProcess(-1)
            }
        }
    }

    private fun doDifferentialTestingOneRound() {
        val printer = IrProgramPrinter(false)
        val generator = IrDeclGeneratorImpl(
            GeneratorConfig(
                classMemberIsPropertyWeight = 0,
                allowUnitInTypeArgument = true,
                printJavaNullableAnnotationProbability = 0f
            ),
            majorLanguage = Language.GROOVY4,
        )
        val program = generator.genProgram()
        repeat(langShuffleTimesBeforeMutate) {
            run normalDifferential@{
                runDifferential(groovyCompilers, generator, printer, program, stopOnErrors)
            }
            generator.shuffleLanguage(program)
        }
        val mutator = IrMutatorImpl(
            generator = generator,
            config = MutatorConfig(
                mutateGenericArgumentInParentWeight = 1,
                removeOverrideMemberFunctionWeight = 1,
                mutateGenericArgumentInMemberFunctionParameterWeight = 1,
                mutateParameterNullabilityWeight = 0
            )
        )
        if (mutator.mutate(program)) {
            repeat(langShuffleTimesAfterMutate) {
                run mutatedDifferential@{
                    runDifferential(groovyCompilers, generator, printer, program, stopOnErrors)
                }
                generator.shuffleLanguage(program)
            }
        }
    }

    private fun doOneRound() {
        for (groovyCompiler in groovyCompilers) {
            val printer = IrProgramPrinter(false)
            val generator = IrDeclGeneratorImpl(
                GeneratorConfig(
                    classMemberIsPropertyWeight = 0,
                    allowUnitInTypeArgument = true,
                    printJavaNullableAnnotationProbability = 0f
                ),
                majorLanguage = groovyCompiler.language,
            )
            val program = generator.genProgram()
            repeat(langShuffleTimesBeforeMutate) {
                val compileResult = groovyCompiler.compileGroovyWithJava(printer, program)
                if (!compileResult.success) {
                    recordCompileResult(groovyCompiler.language, printer.printToSingle(program), compileResult)
                    if (stopOnErrors) {
                        exitProcess(-1)
                    }
                }
                generator.shuffleLanguage(program)
            }
        }
    }

}

fun main(args: Array<String>) {
    return CodeSmithGroovyRunner().main(args)
}