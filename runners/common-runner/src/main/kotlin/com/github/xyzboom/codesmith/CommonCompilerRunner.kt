package com.github.xyzboom.codesmith

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.github.xyzboom.codesmith.RunMode.NormalTest
import com.github.xyzboom.codesmith.RunMode.DifferentialTest
import com.github.xyzboom.codesmith.RunMode.GenerateIROnly
import com.github.xyzboom.codesmith.config.RunConfig
import com.github.xyzboom.codesmith.serde.configGson
import java.io.File

abstract class CommonCompilerRunner : CliktCommand(), ICompilerRunner {

    init {
        context {
            helpFormatter = { MordantHelpFormatter(it, showDefaultValues = true) }
        }
    }

    protected val runMode by option("-m", "--mode")
        .enum<RunMode> {
            when (it) {
                NormalTest -> "normal"
                DifferentialTest -> "diff"
                GenerateIROnly -> "ironly"
            }
        }
        .default(DifferentialTest, "diff")
        .help {
            val sb = StringBuilder("Run mode.\u0085")
            for (mode in RunMode.entries) {
                val modeHelpString = when (mode) {
                    NormalTest -> "[normal]: Generate IR automatically. Test compiler(s) normally (not differentially).\u0085"
                    DifferentialTest -> "[diff]: Generate IR automatically. Test compiler(s) differentially.\u0085"
                    GenerateIROnly -> "[ironly]: Generate and save IR automatically only. Do no tests.\u0085"
                }
                sb.append(modeHelpString)
            }
            sb.toString()
        }
    protected val inputIR by option("-i", "--input")
        .file(
            mustExist = true,
            canBeFile = true,
            canBeDir = false,
            mustBeReadable = true
        ).help("Use input IR file instead of generated. Only run one time.")
    protected val stopOnErrors by option("-s", "--stop-on-errors").boolean().default(false)
    private val configFile by option("--config-file").file(
        mustExist = true,
        canBeFile = true,
        canBeDir = false,
        mustBeReadable = true
    ).default(File("config/default.json"))
    protected lateinit var runConfig: RunConfig
    protected val nonSimilarOutDir by option("-nso", "--non-similar-out").file(
        mustExist = false,
        canBeFile = false,
        canBeDir = true,
        mustBeReadable = true
    ).default(File("out/min"))

    protected val generateIROnlyOutDir by option("-iro", "--ir-out").file(
        mustExist = false,
        canBeFile = false,
        canBeDir = true,
        mustBeReadable = true
    ).default(File("out/ir"))

    abstract val availableCompilers: Map<String, ICompiler>

    abstract val defaultCompilers: Map<String, ICompiler>

    abstract fun runnerMain()

    final override fun run() {
        runConfig = configFile.reader().use {
            configGson.fromJson(it, RunConfig::class.java)
        }
        runnerMain()
    }
}