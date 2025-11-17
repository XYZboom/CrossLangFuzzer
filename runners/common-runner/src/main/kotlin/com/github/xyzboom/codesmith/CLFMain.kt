package com.github.xyzboom.codesmith

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.output.Localization
import java.util.*
import kotlin.system.exitProcess

class CLFMain : CliktCommand() {
    /**
     * Replace "command" into "runners" in help messages
     */
    class HelpMsgReplacer : Localization {
        override fun commandMetavar(): String = "runner"

        override fun commandsTitle(): String = "Runners"
    }

    init {
        context {
            localization = HelpMsgReplacer()
        }
    }

    override val invokeWithoutSubcommand = true
    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            throw PrintHelpMessage(currentContext)
        }
        echo("Starting CrossLangFuzzer")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val runnerLoader: ServiceLoader<CommonCompilerRunner> = ServiceLoader.load(CommonCompilerRunner::class.java)
            val runners = runnerLoader.toList()
            if (runners.isEmpty()) {
                println("No runners loaded, please check your classpath.")
                exitProcess(-1)
            }
            CLFMain().subcommands(runners).main(args)
        }
    }
}