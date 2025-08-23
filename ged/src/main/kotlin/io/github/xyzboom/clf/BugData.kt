package io.github.xyzboom.clf

import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.serde.gson
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.xyzboom.gedlib.GEDEnv

data class BugData(
    val name: String,
    val program: IrProgram,
    val lower: Double,
    val upper: Double,
) {
    companion object {
        private val logger = KotlinLogging.logger {}
        const val RESOURCE_PREFIX = "crosslangfuzzer/bugdata"
        fun fromResource(name: String): IrProgram {
            val stream = this::class.java.classLoader.getResourceAsStream("${RESOURCE_PREFIX}/${name}")
            stream ?: throw IllegalArgumentException("BugData $name not found")
            return stream.reader().use {
                gson.fromJson(it, IrProgram::class.java)
            }
        }
        @JvmField
        val KT78819 = BugData(
            name = "KT78819",
            fromResource("kt-78819-min.json"),
            25.0, Double.MAX_VALUE
        )

        val allBugs = listOf(
            KT78819
        )

        fun GEDEnv.similarToAnyExistedBug(progMin: IrProgram): Boolean {
            for ((name, bug, bugLower, bugUpper) in allBugs) {
                val lower = lowerBoundOf(bug, progMin)
                val upper = upperBoundOf(bug, progMin)
                logger.info { "compare to $name: $lower, $upper" }
                if (lower < bugLower && upper < bugUpper) return true
            }
            return false
        }
    }



}