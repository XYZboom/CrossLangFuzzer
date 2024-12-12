package com.github.xyzboom.codesmith.runner

import java.io.File
import java.io.FileNotFoundException
import java.util.*

object CompilerRunner {
    private val kotlincPath: String = (System.getProperty("kotlincPath")
        ?: throw IllegalStateException("System property 'kotlincPath' not set"))

    internal val kotlincFile: File = kotlincPath.let {
        File(it).also { file ->
            if (!file.exists()) {
                throw FileNotFoundException("Path for 'kotlincPath' $it not exists!")
            }
            if (!file.isFile) {
                throw IllegalStateException("Path for 'kotlincPath' $it is not a file!")
            }
        }
    }

    private val os = System.getProperty("os.name").lowercase(Locale.getDefault())

    @JvmStatic
    fun compile(vararg args: String) {
        val process = ProcessBuilder(kotlincPath, *args).apply {
            environment()["JAVA_HOME"] = System.getProperty("java.home")
        }.start()
        process.waitFor()
        val exitValue = process.exitValue()
        println(exitValue)
        println(process.inputStream.reader().readText())
        println(process.errorStream.reader().readText())
    }

    @JvmStatic
    fun main(args: Array<String>) {
        compile(*args)
    }
}