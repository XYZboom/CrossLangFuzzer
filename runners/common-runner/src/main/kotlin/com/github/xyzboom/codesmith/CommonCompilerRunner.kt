package com.github.xyzboom.codesmith

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.file
import com.github.xyzboom.codesmith.config.RunConfig
import com.github.xyzboom.codesmith.serde.configGson
import java.io.File

abstract class CommonCompilerRunner : CliktCommand(), ICompilerRunner {
    protected val differentialTesting by option("--dt").boolean().default(true)
    protected val stopOnErrors by option("-s", "--stop-on-errors").boolean().default(false)
    private val configFile by option("--config-file").file(
        mustExist = true,
        canBeFile = true,
        canBeDir = false,
        mustBeReadable = true
    ).default(File("config/default.json"))
    protected lateinit var runConfig: RunConfig

    abstract fun runnerMain()

    final override fun run() {
        runConfig = configFile.reader().use {
            configGson.fromJson(it, RunConfig::class.java)
        }
        runnerMain()
    }
}