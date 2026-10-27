package com.github.xyzboom.codesmith.kotlin

import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.serde.gson
import com.google.gson.JsonObject
import org.jetbrains.kotlin.incremental.ChangedFiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration

class K2IncrementalRunnerIntegrationTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `failure diagnostic oracle ignores progress output and warnings`() {
        val archivedDiagnostics = listOf(
            "OUTPUT|Output: /tmp/incremental/classes|<SRC>/A.kt",
            "WARNING|Modifier is redundant|<SRC>/A.kt",
            "ERROR|unresolved reference|<SRC>/A.kt:4",
            "EXCEPTION|compiler threw java.lang.IllegalStateException|<SRC>",
        )
        assertEquals(
            listOf(
                "ERROR|unresolved reference|<SRC>/A.kt:4",
                "EXCEPTION|compiler threw java.lang.IllegalStateException|<SRC>",
            ),
            errorDiagnosticsFrom(archivedDiagnostics),
        )
    }

    @Test
    fun `both failed compile paths are provisionally treated as equivalent`() {
        assertTrue(
            incrementalCleanMismatchReasons(
                incrementalSuccess = false,
                cleanSuccess = false,
                incrementalClassHashes = mapOf("stale.class" to "incremental-hash"),
                cleanClassHashes = emptyMap(),
                binaryOutputsEqual = null,
            ).isEmpty(),
            "Different compiler stages and diagnostics remain archived but do not create a mismatch when both builds fail",
        )
        assertEquals(
            listOf("compile success differs"),
            incrementalCleanMismatchReasons(
                incrementalSuccess = true,
                cleanSuccess = false,
                incrementalClassHashes = emptyMap(),
                cleanClassHashes = emptyMap(),
                binaryOutputsEqual = null,
            ),
        )
        assertEquals(
            listOf("successful class outputs differ"),
            incrementalCleanMismatchReasons(
                incrementalSuccess = true,
                cleanSuccess = true,
                incrementalClassHashes = mapOf("A.class" to "inc"),
                cleanClassHashes = mapOf("A.class" to "clean"),
                binaryOutputsEqual = false,
            ),
        )
        assertTrue(
            incrementalCleanMismatchReasons(
                incrementalSuccess = true,
                cleanSuccess = true,
                incrementalClassHashes = mapOf("A.class" to "same"),
                cleanClassHashes = mapOf("A.class" to "same"),
                binaryOutputsEqual = true,
            ).isEmpty(),
        )
    }

    @Test
    fun `outcome-only oracle flags either asymmetric direction and ignores symmetric outcomes`() {
        assertEquals(listOf("compile success differs"), incrementalOutcomeMismatchReasons(false, true))
        assertEquals(listOf("compile success differs"), incrementalOutcomeMismatchReasons(true, false))
        assertTrue(incrementalOutcomeMismatchReasons(false, false).isEmpty())
        assertTrue(incrementalOutcomeMismatchReasons(true, true).isEmpty())
    }

    @Test
    fun `mismatch archive preserves sources and comparison evidence without compiler outputs`() {
        val fuzzer = K2IncrementalFuzzer(
            generatorConfig = GeneratorConfig.default,
            baseSeed = 1,
            maxCompilerSubmissions = 8,
            scheduleRepeatCount = 1,
            outputDirectory = tempDir.toFile(),
            reserveCompilerGroup = { false },
            recordCompilerReturn = {},
            reportRound = {},
            reportMismatch = {},
        )
        val reportDirectory = tempDir.resolve("mismatch")
        val report = K2IncrementalMismatchReport(
            sources = mapOf("src/Outer.kt" to "class Outer\n"),
            irJson = "{\"program\":\"fixture\"}",
            stage = "SOURCE_CHANGE",
            testRoundIndex = 3,
            roundStep = 2,
            outputAction = "DELETE classes/A.class\n",
            incrementalResultJson = "{\"exitCode\":\"OK\",\"iterations\":[{\"sourceFiles\":[]}]}",
            cleanResultJson = "{\"exitCode\":\"COMPILATION_ERROR\",\"diagnostics\":[\"override\"]}",
            binaryComparisonJson = "{\"compared\":false,\"equal\":null}",
            differences = listOf("compile success differs"),
        )

        fuzzer.archiveMismatchReport(reportDirectory, report)

        assertEquals("class Outer\n", Files.readString(reportDirectory.resolve("sources/src/Outer.kt")))
        assertEquals(report.irJson, Files.readString(reportDirectory.resolve("ir.json")))
        assertEquals("SOURCE_CHANGE\n", Files.readString(reportDirectory.resolve("stage.txt")))
        assertTrue(Files.readString(reportDirectory.resolve("round.txt")).contains("round_step=2/4"))
        assertEquals(report.outputAction, Files.readString(reportDirectory.resolve("output-action.txt")))
        assertEquals(report.incrementalResultJson, Files.readString(reportDirectory.resolve("incremental-result.json")))
        assertEquals(report.cleanResultJson, Files.readString(reportDirectory.resolve("clean-result.json")))
        assertEquals(report.binaryComparisonJson, Files.readString(reportDirectory.resolve("binary-comparison.json")))
        assertEquals("compile success differs\n", Files.readString(reportDirectory.resolve("mismatch.txt")))
        val paths = Files.walk(reportDirectory).use { it.toList() }
        assertTrue(paths.none { it.fileName.toString() in setOf("cache", "classes", "classpath-snapshots") })
        assertTrue(paths.none { Files.isRegularFile(it) && it.fileName.toString().endsWith(".class") })
    }

    @Test
    fun `stop on errors archives the actual compiler observations and stops at the mismatch`() {
        var mismatchReports = 0
        var completedSubmissions = 0
        var forceMismatch = true
        val summaries = mutableListOf<String>()
        val maxSubmissions = 8

        K2IncrementalFuzzer(
            generatorConfig = GeneratorConfig(
                javaRatio = 0.5f,
                topLevelDeclRange = 4..4,
                classHasSuperProbability = 0f,
                classImplNumRange = 0..0,
                classMemberNumRange = 0..0,
                classHasTypeParameterProbability = 0f,
                moduleNumRange = 1..1,
            ),
            baseSeed = 20261027,
            maxCompilerSubmissions = maxSubmissions,
            scheduleRepeatCount = 1,
            outputDirectory = tempDir.toFile(),
            reserveCompilerGroup = { true },
            recordCompilerReturn = { completedSubmissions++ },
            reportRound = {},
            reportMismatch = { mismatchReports++ },
            reportSummary = { summaries += it },
            stopOnErrors = true,
            mismatchClassifier = { incrementalSuccess, cleanSuccess, incrementalHashes, cleanHashes, binaryEqual ->
                if (forceMismatch) {
                    forceMismatch = false
                    listOf("test-injected mismatch")
                } else {
                    incrementalCleanMismatchReasons(
                        incrementalSuccess,
                        cleanSuccess,
                        incrementalHashes,
                        cleanHashes,
                        binaryEqual,
                    )
                }
            },
        ).run()

        assertEquals(1, mismatchReports)
        assertEquals(2, completedSubmissions, "one incremental/clean pair should be archived before stopping")
        assertTrue(summaries.single().contains("compilerMismatches=1"), summaries.single())
        val campaign = childDirectories(tempDir.resolve("incremental")).single()
        val program = childDirectories(campaign).single()
        assertTrue(Files.isRegularFile(program.resolve("program.json")))
        assertTrue(Files.isRegularFile(program.resolve("seed.txt")))
        assertTrue(Files.isRegularFile(program.resolve("repeats/repeat-000/schedule.txt")))
        val snapshots = program.resolve("repeats/repeat-000/scc-validity/snapshots")
        assertEquals(listOf("0000"), childDirectories(snapshots).map { it.fileName.toString() })
        val mismatch = snapshots.resolve("0000")
        assertTrue(Files.isRegularFile(mismatch.resolve("ir.json")))
        val archivedSourceFiles = Files.walk(mismatch.resolve("sources")).use { paths ->
            paths.filter { Files.isRegularFile(it) }.count()
        }
        assertTrue(archivedSourceFiles > 0, "source files should be archived")
        assertTrue(Files.readString(mismatch.resolve("mismatch.txt")).contains("test-injected mismatch"))
        val incrementalResult = gson.fromJson(
            Files.readString(mismatch.resolve("incremental-result.json")),
            JsonObject::class.java,
        )
        val cleanResult = gson.fromJson(
            Files.readString(mismatch.resolve("clean-result.json")),
            JsonObject::class.java,
        )
        val binaryComparison = gson.fromJson(
            Files.readString(mismatch.resolve("binary-comparison.json")),
            JsonObject::class.java,
        )
        assertEquals("OK", incrementalResult.get("exitCode").asString)
        assertEquals("OK", cleanResult.get("exitCode").asString)
        assertTrue(incrementalResult.getAsJsonObject("classHashes").size() > 0)
        assertTrue(cleanResult.getAsJsonObject("classHashes").size() > 0)
        assertTrue(incrementalResult.getAsJsonArray("iterations").size() > 0)
        assertTrue(cleanResult.getAsJsonArray("iterations").size() > 0)
        assertTrue(binaryComparison.get("compared").asBoolean)
        assertTrue(binaryComparison.get("equal").asBoolean)
        val paths = Files.walk(mismatch).use { it.toList() }
        assertTrue(paths.none { it.fileName.toString() in setOf("cache", "classes", "classpath-snapshots") })
        assertTrue(paths.none { Files.isRegularFile(it) && it.fileName.toString().endsWith(".class") })
    }

    @Test
    fun `default campaign completes the round and archives every mismatching snapshot`() {
        var reservedSubmissions = 0
        var completedSubmissions = 0
        var mismatchReports = 0
        var forcedMismatchCount = 0
        val maxSubmissions = 8
        val summaries = mutableListOf<String>()

        K2IncrementalFuzzer(
            generatorConfig = GeneratorConfig(
                javaRatio = 0.5f,
                topLevelDeclRange = 4..4,
                classHasSuperProbability = 0f,
                classImplNumRange = 0..0,
                classMemberNumRange = 0..0,
                classHasTypeParameterProbability = 0f,
                moduleNumRange = 1..1,
            ),
            baseSeed = 20261028,
            maxCompilerSubmissions = maxSubmissions,
            scheduleRepeatCount = 1,
            outputDirectory = tempDir.toFile(),
            reserveCompilerGroup = { size ->
                if (reservedSubmissions + size > maxSubmissions) false
                else {
                    reservedSubmissions += size
                    true
                }
            },
            recordCompilerReturn = { completedSubmissions++ },
            reportRound = {},
            reportMismatch = { mismatchReports++ },
            reportSummary = { summaries += it },
            mismatchClassifier = { incrementalSuccess, cleanSuccess, incrementalHashes, cleanHashes, binaryEqual ->
                if (forcedMismatchCount < 2) {
                    forcedMismatchCount++
                    listOf("test-injected mismatch $forcedMismatchCount")
                } else {
                    incrementalCleanMismatchReasons(
                        incrementalSuccess,
                        cleanSuccess,
                        incrementalHashes,
                        cleanHashes,
                        binaryEqual,
                    )
                }
            },
        ).run()

        assertEquals(8, completedSubmissions, "the default must finish the reserved round after a mismatch")
        assertEquals(2, mismatchReports)
        assertTrue(summaries.single().contains("compilerMismatches=2"), summaries.single())
        assertTrue(summaries.single().contains("sccValidityPairs=4"), summaries.single())
        val campaign = childDirectories(tempDir.resolve("incremental")).single()
        val program = childDirectories(campaign).single()
        assertTrue(Files.isRegularFile(program.resolve("program.json")))
        assertTrue(Files.isRegularFile(program.resolve("seed.txt")))
        assertTrue(Files.isRegularFile(program.resolve("repeats/repeat-000/schedule.txt")))
        val snapshots = program.resolve("repeats/repeat-000/scc-validity/snapshots")
        assertEquals(listOf("0000", "0001"), childDirectories(snapshots).map { it.fileName.toString() })
        assertTrue(Files.readString(snapshots.resolve("0000/mismatch.txt")).contains("test-injected mismatch 1"))
        assertTrue(Files.readString(snapshots.resolve("0001/mismatch.txt")).contains("test-injected mismatch 2"))
    }

    @Test
    fun `stop-on-errors controls continuation after a trajectory error`() {
        for (stopOnErrors in listOf(false, true)) {
            val runDirectory = tempDir.resolve("stop-on-errors-$stopOnErrors")
            val outputDirectory = runDirectory.resolve("out")
            val errorDirectory = runDirectory.resolve("errors")
            var failFirstReservation = true
            var reservedSubmissions = 0
            var completedSubmissions = 0
            val summaries = mutableListOf<String>()

            K2IncrementalFuzzer(
                generatorConfig = GeneratorConfig(
                    javaRatio = 0.5f,
                    topLevelDeclRange = 4..4,
                    classHasSuperProbability = 0f,
                    classImplNumRange = 0..0,
                    classMemberNumRange = 0..0,
                    classHasTypeParameterProbability = 0f,
                    moduleNumRange = 1..1,
                ),
                baseSeed = if (stopOnErrors) 20261030 else 20261029,
                maxCompilerSubmissions = 8,
                scheduleRepeatCount = 1,
                outputDirectory = outputDirectory.toFile(),
                errorOutputDirectory = errorDirectory.toFile(),
                reserveCompilerGroup = { size ->
                    if (failFirstReservation) {
                        failFirstReservation = false
                        throw IllegalStateException("test-injected trajectory error")
                    }
                    if (reservedSubmissions + size > 8) false
                    else {
                        reservedSubmissions += size
                        true
                    }
                },
                recordCompilerReturn = { completedSubmissions++ },
                reportRound = {},
                reportMismatch = {},
                reportSummary = { summaries += it },
                stopOnErrors = stopOnErrors,
            ).run()

            assertEquals(1, summaries.size)
            assertTrue(summaries.single().contains("trajectoryErrors=1"), summaries.single())
            val errorFiles = Files.walk(errorDirectory).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.fileName.toString() == "trajectory-error.txt" }.toList()
            }
            assertEquals(1, errorFiles.size)
            assertTrue(Files.readString(errorFiles.single()).contains("test-injected trajectory error"))
            if (stopOnErrors) {
                assertEquals(0, completedSubmissions, "stop-on-errors must prevent later trajectories")
            } else {
                assertEquals(8, completedSubmissions, "default behavior must continue into the later parameter trajectory")
                assertTrue(summaries.single().contains("parameterStressPairs=4"), summaries.single())
            }
            assertFalse(Files.exists(outputDirectory.resolve("incremental")), "trajectory errors are not bug reports")
        }
    }

    @Test
    fun `missing incremental outputs mark all current Kotlin sources modified`() {
        val sources = listOf(File("A.kt"), File("B.kt"))
        val changed = incrementalChangedFiles(true, sources) as ChangedFiles.DeterminableFiles.Known
        assertEquals(sources, changed.modified)
        assertTrue(changed.removed.isEmpty())
        assertTrue(incrementalChangedFiles(false, sources) === ChangedFiles.DeterminableFiles.ToBeComputed)
    }

    @Test
    fun `runner archives bounded incremental and clean comparisons only for mismatches`() {
        var reservedSubmissions = 0
        var completedSubmissions = 0
        var mismatchReports = 0
        val reportedRounds = mutableListOf<Duration>()
        val summaries = mutableListOf<String>()
        val maxSubmissions = 8

        K2IncrementalFuzzer(
            generatorConfig = GeneratorConfig.default,
            baseSeed = 771,
            maxCompilerSubmissions = maxSubmissions,
            scheduleRepeatCount = 1,
            outputDirectory = tempDir.toFile(),
            reserveCompilerGroup = { size ->
                if (reservedSubmissions + size > maxSubmissions) false
                else {
                    reservedSubmissions += size
                    true
                }
            },
            recordCompilerReturn = { completedSubmissions++ },
            reportRound = { reportedRounds += it },
            reportMismatch = { mismatchReports++ },
            reportSummary = { summaries += it },
        ).run()

        assertEquals(maxSubmissions, reservedSubmissions)
        assertEquals(maxSubmissions, completedSubmissions)
        assertEquals(1, reportedRounds.size, "Four incremental/clean pairs form one logical test round")
        assertEquals(0, mismatchReports)

        assertFalse(
            Files.exists(tempDir.resolve("incremental")),
            "Successful comparisons must not create per-program report folders",
        )
        val summary = summaries.single()
        assertTrue(summary.contains("pairedComparisons=4"), summary)
        assertTrue(summary.contains("selectiveRecompileProven=true"), summary)
        assertTrue(summary.contains("compilerMismatches=0"), summary)
    }

    @Test
    fun `runner groups generated SCCs into three automatic stages and compares every prefix`() {
        var reservedSubmissions = 0
        var completedSubmissions = 0
        var mismatchReports = 0
        val reportedRounds = mutableListOf<Duration>()
        val summaries = mutableListOf<String>()
        val maxSubmissions = 8

        K2IncrementalFuzzer(
            generatorConfig = GeneratorConfig(
                javaRatio = 0.5f,
                topLevelDeclRange = 4..4,
                classHasSuperProbability = 0.0f,
                classImplNumRange = 0..0,
                classMemberNumRange = 0..0,
                classHasTypeParameterProbability = 0.0f,
                moduleNumRange = 1..1,
            ),
            baseSeed = 1337,
            maxCompilerSubmissions = maxSubmissions,
            scheduleRepeatCount = 1,
            outputDirectory = tempDir.toFile(),
            reserveCompilerGroup = { size ->
                if (reservedSubmissions + size > maxSubmissions) false
                else {
                    reservedSubmissions += size
                    true
                }
            },
            recordCompilerReturn = { completedSubmissions++ },
            reportRound = { reportedRounds += it },
            reportMismatch = { mismatchReports++ },
            reportSummary = { summaries += it },
            sccStageCount = 3,
        ).run()

        assertEquals(8, reservedSubmissions)
        assertEquals(8, completedSubmissions)
        assertEquals(1, reportedRounds.size, "The empty baseline plus three SCC stages form one four-compile test round")
        val summary = summaries.single()
        assertTrue(summary.contains("sccValidityPairs=4"), summary)
        assertTrue(summary.contains("compilerMismatches=0"), summary)

        assertFalse(
            Files.exists(tempDir.resolve("incremental")),
            "Matching SCC comparisons must not create per-program report folders",
        )
        assertEquals(0, mismatchReports)
    }

    private fun childDirectories(path: Path): List<Path> = Files.list(path).use { paths ->
        paths.filter { Files.isDirectory(it) }.sorted().toList()
    }
}
