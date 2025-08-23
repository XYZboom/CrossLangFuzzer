package io.github.xyzboom.clf

import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.serde.gson
import io.github.xyzboom.gedlib.GEDEnv
import io.github.xyzboom.gedlib.GEDGraph
import org.junit.jupiter.api.Test
import java.io.File

class Ir2GEDGraphTest {
    @Test
    fun test() {
        BugData.KT78819.let(::println)
        val root = File("/home/xyzboom/Code/kotlin/CrossLangFuzzer/bugdata/kotlin/KT-78819")
        val files = root.listFiles() ?: return
        var last: GEDGraph? = null
        val env = GEDEnv()
        val jdk9361835 = gson.fromJson(
            File("/home/xyzboom/Code/kotlin/CrossLangFuzzer/bugdata/java/JDK-8361835/000001983a5b941d/main.json").reader(),
            IrProgram::class.java
        )
        val jdk9361835G = jdk9361835.toGEDGraph(env)
        for (dir in files) {
            val jsonFile = File(dir, "main.json")
            val prog = gson.fromJson(jsonFile.reader(), IrProgram::class.java)
            val now = prog.toGEDGraph(env)
            val lower1 = env.getLowerBound(jdk9361835G, now)
            val upper1 = env.getUpperBound(jdk9361835G, now)
            println("compare to jdk: " + (lower1 to upper1).toString())
            if (last == null) {
                last = now
                continue
            }
            val lower = env.getLowerBound(now, last)
            val upper = env.getUpperBound(now, last)
            println(lower to upper)
            last = now
        }
    }
}