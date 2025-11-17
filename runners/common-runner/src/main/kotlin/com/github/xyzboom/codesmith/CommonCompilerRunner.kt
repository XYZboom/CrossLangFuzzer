package com.github.xyzboom.codesmith

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.github.xyzboom.codesmith.RunMode.NormalTest
import com.github.xyzboom.codesmith.RunMode.DifferentialTest
import com.github.xyzboom.codesmith.RunMode.GenerateIROnly
import com.github.xyzboom.codesmith.RunMode.ReduceOnly
import com.github.xyzboom.codesmith.config.RunConfig
import com.github.xyzboom.codesmith.serde.configGson
import java.io.File

abstract class CommonCompilerRunner(
    name: String? = null
) : CliktCommand(name), ICompilerRunner {

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
                ReduceOnly -> "reduce"
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
                    ReduceOnly -> "[reduce]: Reduce the input IR only.\u0085"
                }
                sb.append(modeHelpString)
            }
            sb.toString()
        }
    private val inputIR by option("-i", "--input")
        .file(
            mustExist = true,
            canBeFile = true,
            canBeDir = true,
            mustBeReadable = true
        ).help(
            "Use input IR file instead of generated. If input is a directory, " +
                    "all the json files in it will be used as input IR files."
        )
    private val recursivelyInput by option("--recursively-input", "-ri")
        .flag()
        .help("Recursively search for JSON files within the input folder")
    protected val useCache by option("--use-cache", "-u")
        .flag()
        .help("Use cached reduced IR file for reduce only mode if exists. Save cache also.")
    protected val stopOnErrors by option("-s", "--stop-on-errors")
        .flag()
        .help("Stop the runner when find a compiler bug.")
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

    protected val enableGED by option("--enable-ged")
        .flag()
        .help("Enable gedlib for program similarity compare. DEVELOP ONLY!")

    protected val inputIRFiles: Sequence<File>?
        get() {
            val inputIR = inputIR ?: return null
            if (inputIR.isFile) {
                return sequenceOf(inputIR)
            }
            return if (recursivelyInput) {
                inputIR.walk()
            } else {
                inputIR.walk().maxDepth(1)
            }.filter { it.isFile && it.extension.lowercase() == "json" }
        }

    abstract val availableCompilers: Map<String, ICompiler>

    abstract val defaultCompilers: Map<String, ICompiler>

    abstract fun runnerMain()

    final override fun run() {
        runConfig = if (!configFile.exists()) {
            RunConfig()
        } else {
            configFile.reader().use {
                configGson.fromJson(it, RunConfig::class.java)
            }
        }
        runnerMain()
    }
}