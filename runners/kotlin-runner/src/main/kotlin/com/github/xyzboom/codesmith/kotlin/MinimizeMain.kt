package com.github.xyzboom.codesmith.kotlin

import com.github.ajalt.clikt.parsers.CommandLineParser
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.minimize.MinimizeRunnerImpl
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.github.xyzboom.codesmith.serde.gson
import io.github.xyzboom.clf.BugData
import io.github.xyzboom.clf.lowerBoundOf
import io.github.xyzboom.clf.toGEDGraph
import io.github.xyzboom.clf.upperBoundOf
import io.github.xyzboom.gedlib.GEDEnv
import io.github.xyzboom.gedlib.GEDGraph
import java.io.File

object MinimizeMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val runner = CrossLangFuzzerKotlinRunner()
        val m1 = gson.fromJson(File("/home/xyzboom/Code/kotlin/CrossLangFuzzer/out/raw/00000198fdb7d7e5/main-min.json").reader(),
            IrProgram::class.java)
        println(m1)
        val progStr = IrProgramPrinter().printToSingle(m1)
        println(progStr)
        CommandLineParser.parseAndRun(runner, listOf(/*"--dt=false"*/)) {
            it as CrossLangFuzzerKotlinRunner
//            val compileM1 = it.compile(m1, )
//            println(compileM1)
//            val (m1Min, _) = MinimizeRunnerImpl(it).minimize(
//                m1, it.compile(m1,)
//            )
//            File("/home/xyzboom/Code/kotlin/CrossLangFuzzer/out/0000019898a7cdbe/main-min.json")
//                .writer().use { writer ->
//                    gson.toJson(m1Min, writer)
//                }
////            val root = File("/home/xyzboom/Code/kotlin/CrossLangFuzzer/bugdata/kotlin/KT-78819")
//            val root = File("/home/xyzboom/Code/kotlin/CrossLangFuzzer/bugdata/java/JDK-8361835")
//            val files = root.listFiles() ?: return
//            var lastMin: IrProgram? = null
//            val env = GEDEnv()
            /*val jdk9361835 = gson.fromJson(
                File("/home/xyzboom/Code/kotlin/CrossLangFuzzer/bugdata/java/JDK-8361835/000001983a2501e4/main.json").reader(),
                IrProgram::class.java
            )
            val (jdk9361835Min, _) = MinimizeRunnerImpl(it).minimize(
                jdk9361835, it.compile(jdk9361835)
            )
            val jdk9361835G = jdk9361835Min.toGEDGraph(env)
            val visited = mutableSetOf<GEDGraph>()
            for ((i, dir) in files.toList().withIndex()) {
                println(i)
                val jsonFile = File(dir, "main-min.json")
                val progMin = gson.fromJson(jsonFile.reader(), IrProgram::class.java)
//                val dur = measureTime {
//                    val pair = MinimizeRunnerImpl(it).minimize(
//                        prog, it.compile(prog)
//                    )
//                    progMin = pair.first
//                }
//                println(dur)
                val now = progMin.toGEDGraph(env)
//                val lower1 = env.getLowerBound(jdk9361835G, now)
//                val upper1 = env.getUpperBound(jdk9361835G, now)
//                println("compare to jdk: " + (lower1 to upper1).toString())
                /*for (graph in visited) {
                    val lower = env.getLowerBound(now, graph)
                    val upper = env.getUpperBound(now, graph)
                    println(lower to upper)
                }*/
                val lower = env.lowerBoundOf(progMin, BugData.KT78819.program)
                val upper = env.upperBoundOf(progMin, BugData.KT78819.program)
                println(lower to upper)
                val similar = with(BugData) {
                    env.similarToAnyExistedBug(progMin)
                }
                println(similar)
                lastMin = progMin
                visited.add(now)
            }*/
        }
    }
}