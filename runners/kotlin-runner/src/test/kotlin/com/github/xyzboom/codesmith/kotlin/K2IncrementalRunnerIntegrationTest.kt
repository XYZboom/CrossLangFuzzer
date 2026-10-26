package com.github.xyzboom.codesmith.kotlin

import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
    fun `missing incremental outputs mark all current Kotlin sources modified`() {
        val sources = listOf(File("A.kt"), File("B.kt"))
        val changed = incrementalChangedFiles(true, sources) as ChangedFiles.DeterminableFiles.Known
        assertEquals(sources, changed.modified)
        assertTrue(changed.removed.isEmpty())
        assertTrue(incrementalChangedFiles(false, sources) === ChangedFiles.DeterminableFiles.ToBeComputed)
    }

    @Test
    fun `runner archives bounded incremental and clean comparisons`() {
        var reservedSubmissions = 0
        var completedSubmissions = 0
        var mismatchReports = 0
        val reportedRounds = mutableListOf<Duration>()
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
        ).run()

        assertEquals(maxSubmissions, reservedSubmissions)
        assertEquals(maxSubmissions, completedSubmissions)
        assertEquals(1, reportedRounds.size, "Four incremental/clean pairs form one logical test round")
        assertEquals(0, mismatchReports)

        val campaignRoots = childDirectories(tempDir.resolve("incremental"))
        assertEquals(1, campaignRoots.size)
        val programs = childDirectories(campaignRoots.single())
        assertEquals(1, programs.size)
        val program = programs.single()
        assertTrue(Files.isRegularFile(program.resolve("program.json")))
        val programJsonText = Files.readString(program.resolve("program.json"))
        assertTrue(programJsonText.contains("\"language\":\"KOTLIN\""), "Archived program must contain Kotlin declarations")
        assertTrue(programJsonText.contains("\"language\":\"JAVA\""), "Archived program must contain Java declarations")
        assertTrue(Files.isRegularFile(program.resolve("seed.txt")))

        val repeats = childDirectories(program.resolve("repeats"))
        assertEquals(1, repeats.size)
        val repeat = repeats.single()
        val schedule = Files.readString(repeat.resolve("schedule.txt"))
        assertTrue(schedule.contains("generation_seed=771"))
        assertTrue(schedule.contains("schedule_repeat=0"))
        assertTrue(Files.isDirectory(repeat.resolve("scc-validity/incremental/cache")))

        val snapshots = childDirectories(repeat.resolve("scc-validity/snapshots"))
        assertEquals(4, snapshots.size)
        snapshots.forEachIndexed { snapshotIndex, snapshot ->
            assertTrue(Files.isRegularFile(snapshot.resolve("ir.json")))
            assertTrue(Files.isRegularFile(snapshot.resolve("incremental-result.json")))
            assertTrue(Files.isRegularFile(snapshot.resolve("clean-result.json")))
            val roundMetadata = Files.readString(snapshot.resolve("round.txt"))
            assertTrue(roundMetadata.contains("test_round_index=0"))
            assertTrue(roundMetadata.contains("round_step=${snapshotIndex + 1}/4"))
            assertTrue(Files.isDirectory(snapshot.resolve("sources")))

            val incremental = readJson(snapshot.resolve("incremental-result.json"))
            val clean = readJson(snapshot.resolve("clean-result.json"))
            assertEquals("OK", incremental.get("exitCode").asString)
            assertEquals("OK", clean.get("exitCode").asString)
            assertEquals("OK", incremental.get("kotlinExitCode").asString)
            assertEquals("OK", clean.get("kotlinExitCode").asString)
            assertEquals("OK", incremental.get("javaExitCode").asString)
            assertEquals("OK", clean.get("javaExitCode").asString)
            assertTrue(incremental.getAsJsonArray("javaDiagnostics") != null)
            assertTrue(clean.getAsJsonArray("javaDiagnostics") != null)
            assertTrue(incremental.getAsJsonArray("errorDiagnostics").isEmpty())
            assertTrue(clean.getAsJsonArray("errorDiagnostics").isEmpty())
            assertTrue(incremental.get("thrownClass") == null || incremental.get("thrownClass").isJsonNull)
            assertTrue(clean.get("thrownClass") == null || clean.get("thrownClass").isJsonNull)
            assertEquals(incremental.get("classHashes"), clean.get("classHashes"))
            val binaryComparison = readJson(snapshot.resolve("binary-comparison.json"))
            assertTrue(binaryComparison.get("compared").asBoolean)
            assertTrue(binaryComparison.get("equal").asBoolean)
            assertEquals(incremental.getAsJsonObject("classHashes").size(), binaryComparison.get("classFileCount").asInt)
            assertTrue(binaryComparison.getAsJsonArray("differingClassFiles").isEmpty())
            val classHashNames = incremental.getAsJsonObject("classHashes").keySet()
            assertTrue(classHashNames.any { it.startsWith("kotlin/") }, "Kotlin class output must be archived")
            assertTrue(classHashNames.any { it.startsWith("java/") }, "Java class output must be archived")
            assertFalse(Files.exists(snapshot.resolve("mismatch.txt")))
        }

        val secondSnapshot = snapshots.last()
        val sourceCount = Files.walk(secondSnapshot.resolve("sources")).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }.count().toInt()
        }
        assertTrue(sourceCount > 1, "Seed 771 should produce enough Kotlin sources to observe selective compilation")
        val incremental = readJson(secondSnapshot.resolve("incremental-result.json"))
        val selectiveCompile = incremental.getAsJsonArray("iterations").any { iteration ->
            val entry = iteration.asJsonObject
            entry.get("incremental").asBoolean &&
                entry.getAsJsonArray("sourceFiles").size() in 1 until sourceCount
        }
        assertTrue(selectiveCompile, "The retained-state incremental compile must compile fewer than all Kotlin sources")
    }

    @Test
    fun `runner groups generated SCCs into three automatic stages and compares every prefix`() {
        var reservedSubmissions = 0
        var completedSubmissions = 0
        var mismatchReports = 0
        val reportedRounds = mutableListOf<Duration>()
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
            sccStageCount = 3,
        ).run()

        assertEquals(8, reservedSubmissions)
        assertEquals(8, completedSubmissions)
        assertEquals(1, reportedRounds.size, "The empty baseline plus three SCC stages form one four-compile test round")

        val campaign = childDirectories(tempDir.resolve("incremental")).single()
        val program = childDirectories(campaign).single()
        val programJsonText = Files.readString(program.resolve("program.json"))
        assertTrue(programJsonText.contains("\"language\":\"KOTLIN\""))
        assertTrue(programJsonText.contains("\"language\":\"JAVA\""))

        val repeat = program.resolve("repeats/repeat-000")
        val schedule = Files.readString(repeat.resolve("schedule.txt"))
        assertTrue(schedule.contains("scc_stage_count_requested=3"))
        assertTrue(schedule.contains("scc_stage_count_actual=3"))
        assertTrue(schedule.contains("scc_validity_snapshots=4"))
        val snapshots = childDirectories(repeat.resolve("scc-validity/snapshots"))
        assertEquals(4, snapshots.size, "Expected the empty baseline plus three automatic SCC stages")

        val expectedClassCountsByPart = listOf(1, 2, 4)
        snapshots.drop(1).forEachIndexed { partIndex, snapshot ->
            assertTrue(Files.readString(snapshot.resolve("stage.txt")).startsWith("ADD_SCC_PART:"))
            val ir = readJson(snapshot.resolve("ir.json"))
            val includedClassCount = ir.getAsJsonArray("modules")[0].asJsonObject.getAsJsonArray("classes").size()
            assertEquals(expectedClassCountsByPart[partIndex], includedClassCount, "Unexpected accumulated class count at part ${partIndex + 1}")

            val incremental = readJson(snapshot.resolve("incremental-result.json"))
            val clean = readJson(snapshot.resolve("clean-result.json"))
            assertEquals("OK", incremental.get("exitCode").asString, "Incremental part ${partIndex + 1}: $incremental")
            assertEquals("OK", clean.get("exitCode").asString, "Clean part ${partIndex + 1}: $clean")
            assertEquals("OK", incremental.get("kotlinExitCode").asString)
            assertEquals("OK", clean.get("kotlinExitCode").asString)
            assertEquals("OK", incremental.get("javaExitCode").asString)
            assertEquals("OK", clean.get("javaExitCode").asString)
            assertEquals(incremental.get("classHashes"), clean.get("classHashes"))

            val binaryComparison = readJson(snapshot.resolve("binary-comparison.json"))
            assertTrue(binaryComparison.get("compared").asBoolean, "Part ${partIndex + 1} needs a byte comparison")
            assertTrue(binaryComparison.get("equal").asBoolean, "Part ${partIndex + 1} class bytes must match")
            assertEquals(
                incremental.getAsJsonObject("classHashes").size(),
                binaryComparison.get("classFileCount").asInt,
            )
            assertTrue(binaryComparison.getAsJsonArray("differingClassFiles").isEmpty())
            assertFalse(Files.exists(snapshot.resolve("mismatch.txt")))
        }
        assertEquals(0, mismatchReports)
    }

    private fun childDirectories(path: Path): List<Path> = Files.list(path).use { paths ->
        paths.filter { Files.isDirectory(it) }.sorted().toList()
    }

    private fun readJson(path: Path): JsonObject = JsonParser.parseString(Files.readString(path)).asJsonObject
}
