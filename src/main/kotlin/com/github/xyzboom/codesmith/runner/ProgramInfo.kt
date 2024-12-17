package com.github.xyzboom.codesmith.runner

import org.jacoco.core.analysis.Analyzer
import org.jacoco.core.analysis.IClassCoverage
import org.jacoco.core.analysis.ICoverageNode
import org.jacoco.core.analysis.ICoverageVisitor
import org.jacoco.core.data.ExecutionDataStore
import java.io.File

class ProgramInfo: ICoverageVisitor {
    private val dataStore = ExecutionDataStore()
    private val analyzer = Analyzer(dataStore, this)

    private val needClasses = HashSet<String>()
    private var totalProbeCount = 0

    override fun visitCoverage(coverage: IClassCoverage) {
        val instCount = coverage.getCounter(ICoverageNode.CounterEntity.INSTRUCTION).totalCount
        needClasses.add("${coverage.name}:${coverage.id}")
        totalProbeCount += instCount
    }

    fun collect(analyzePaths: List<String>, fileFilter: (File) -> Boolean = { true }) {
        totalProbeCount = 0
        for (analyzePath in analyzePaths) {
            val walker = File(analyzePath).walkTopDown().iterator()
            while (walker.hasNext()) {
                val file = walker.next()
                if (fileFilter(file)) {
                    analyzer.analyzeAll(file)
                }
            }
        }
    }

    /**
     * Must call [collect] before call [coverageInfo] to collect class information.
     */
    fun coverageInfo(dataStore: ExecutionDataStore): Pair<Int, Int> {
        var matched = 0
        for (data in dataStore.contents) {
            val classInfo = "${data.name}:${data.id}"
            if (needClasses.contains(classInfo)) {
                val count = data.probes.count { it }
                matched += count
            }
        }
        return matched to totalProbeCount
    }

}