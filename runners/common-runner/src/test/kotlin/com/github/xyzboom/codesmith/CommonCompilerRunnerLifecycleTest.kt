package com.github.xyzboom.codesmith

import com.github.ajalt.clikt.parsers.CommandLineParser
import com.github.xyzboom.codesmith.generator.IrDeclGenerator
import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.classes
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.mutator.MutatorLanguageSemantics
import com.github.xyzboom.codesmith.mutator.SemanticValidity
import com.github.xyzboom.codesmith.serde.gson
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FakeTestCompiler(
    val name: String,
    var onCompile: (IrProgram) -> CompileResult = { CompileResult(name, null, null) }
) : ICompiler {
    val invocations = mutableListOf<IrProgram>()
    override fun compile(program: IrProgram): CompileResult {
        invocations.add(program)
        return onCompile(program)
    }
}

class TestableRunner(
    override val runnerLanguage: Language = Language.KOTLIN,
    override val mutatorLanguageSemantics: MutatorLanguageSemantics = MutatorLanguageSemantics.KOTLIN,
    private val normalList: List<CompilerTarget> = emptyList(),
    private val diffList: List<CompilerTarget> = emptyList(),
    private val customProgram: IrProgram? = null,
    private val mutatorLogic: ((IrProgram, IrDeclGenerator) -> Pair<SemanticValidity, String?>?)? = null,
) : CommonCompilerRunner("test") {
    var normalTargetsCalled = false
    var diffTargetsCalled = false
    var generateCalls = 0
    val generatorLanguages = mutableListOf<Language>()
    val mutatedPrograms = mutableListOf<IrProgram>()
    val prepareTargetsCalled = mutableListOf<CompilerTarget>()

    override fun normalTargets(): List<CompilerTarget> {
        normalTargetsCalled = true
        return normalList
    }

    override fun differentialTargets(): List<CompilerTarget> {
        diffTargetsCalled = true
        return diffList
    }

    override fun createGenerator(majorLanguage: Language): IrDeclGenerator {
        generatorLanguages += majorLanguage
        return super.createGenerator(majorLanguage)
    }

    override fun generateProgram(generator: IrDeclGenerator): IrProgram {
        generateCalls++
        return customProgram ?: super.generateProgram(generator)
    }

    override fun mutateProgram(
        program: IrProgram,
        generator: IrDeclGenerator,
    ): Pair<SemanticValidity, String?>? {
        mutatedPrograms += program
        if (mutatorLogic != null) return mutatorLogic.invoke(program, generator)
        return super.mutateProgram(program, generator)
    }

    override fun prepareProgramForTarget(program: IrProgram, target: CompilerTarget) {
        prepareTargetsCalled.add(target)
        super.prepareProgramForTarget(program, target)
    }
}

class CommonCompilerRunnerLifecycleTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setUp() {
            if (System.getProperty("codesmith.logger.outdir") == null) {
                val logDir = File(System.getProperty("java.io.tmpdir"), "codesmith-test-logs")
                logDir.mkdirs()
                System.setProperty("codesmith.logger.outdir", logDir.absolutePath)
            }
        }
    }

    private fun CommonCompilerRunner.runWithArgs(vararg args: String) {
        CommandLineParser.parseAndRun(this, args.toList()) { it.run() }
    }

    @Test
    fun `test mutation oracle logic`() {
        assertTrue(isMutationOracleSatisfied(SemanticValidity.VALID, compileSuccess = true))
        assertFalse(isMutationOracleSatisfied(SemanticValidity.VALID, compileSuccess = false))
        assertTrue(isMutationOracleSatisfied(SemanticValidity.INVALID, compileSuccess = false))
        assertFalse(isMutationOracleSatisfied(SemanticValidity.INVALID, compileSuccess = true))
        assertThrows<IllegalStateException> {
            isMutationOracleSatisfied(SemanticValidity.UNKNOWN, compileSuccess = true)
        }
    }

    @Test
    fun `1 and 2 target selection and mode dispatch`() {
        val c1 = FakeTestCompiler("c1")
        val c2 = FakeTestCompiler("c2")
        val normalTarget = CompilerTarget("c1", c1, Language.KOTLIN)
        val diffTargets = listOf(CompilerTarget("c1", c1, Language.KOTLIN), CompilerTarget("c2", c2, Language.KOTLIN))

        val normalRunner = TestableRunner(
            normalList = listOf(normalTarget),
            diffList = diffTargets,
            customProgram = buildProgram { },
        )
        normalRunner.runWithArgs("-m", "normal", "--max-rounds", "1")
        assertTrue(normalRunner.normalTargetsCalled)
        assertFalse(normalRunner.diffTargetsCalled)
        assertEquals(1, c1.invocations.size)
        assertEquals(0, c2.invocations.size)

        val diffRunner = TestableRunner(
            normalList = listOf(normalTarget),
            diffList = listOf(CompilerTarget("c1", FakeTestCompiler("c1"), Language.KOTLIN), CompilerTarget("c2", FakeTestCompiler("c2"), Language.KOTLIN)),
            customProgram = buildProgram { },
        )
        diffRunner.runWithArgs("-m", "diff", "--max-rounds", "2")
        assertFalse(diffRunner.normalTargetsCalled)
        assertTrue(diffRunner.diffTargetsCalled)
    }

    @Test
    fun `3 input files respecting requested mode`(@TempDir tempDir: File) {
        val dummyFile = File(tempDir, "dummy.json")
        dummyFile.writeText(gson.toJson(buildProgram { }))

        val runnerIronly = TestableRunner()
        assertThrows<IllegalArgumentException> {
            runnerIronly.runWithArgs("-m", "ironly", "-i", dummyFile.absolutePath)
        }

        val runnerReduce = TestableRunner()
        assertThrows<IllegalArgumentException> {
            runnerReduce.runWithArgs("-m", "reduce")
        }
    }

    @Test
    fun `4 reduction-cache read and write behavior`(@TempDir tempDir: File) {
        val inputFile = File(tempDir, "test.json")
        val prog = buildProgram { }
        inputFile.writeText(gson.toJson(prog))

        val compiler = FakeTestCompiler("c1")
        val target = CompilerTarget("c1", compiler, Language.KOTLIN)

        val runner1 = TestableRunner(diffList = listOf(target))
        runner1.runWithArgs("-m", "reduce", "-i", inputFile.absolutePath, "-u")

        val reducedFile = File(tempDir, "test.json.reduced")
        assertTrue(reducedFile.exists(), "Cache file should have been written")
        val firstInvocations = compiler.invocations.size
        assertTrue(firstInvocations > 0)

        // Second run with cache enabled should read cached file and not re-compile
        val runner2 = TestableRunner(diffList = listOf(target))
        runner2.runWithArgs("-m", "reduce", "-i", inputFile.absolutePath, "-u")
        assertEquals(firstInvocations, compiler.invocations.size, "Cached run should not invoke compiler again")
    }

    @Test
    fun `5 generate-only avoids compilation`(@TempDir tempDir: File) {
        val compiler = FakeTestCompiler("c1")
        val target = CompilerTarget("c1", compiler, Language.KOTLIN)
        val outDir = File(tempDir, "ir-out").apply { mkdirs() }

        val runner = TestableRunner(
            normalList = listOf(target),
            diffList = listOf(target),
            customProgram = buildProgram { },
        )
        runner.runWithArgs("-m", "ironly", "--max-rounds", "2", "-iro", outDir.absolutePath)

        assertEquals(0, compiler.invocations.size, "Generate-only must not compile anything")
        assertTrue(outDir.exists())
        val generatedDirs = outDir.listFiles() ?: emptyArray()
        assertEquals(2, generatedDirs.size)
    }

    @Test
    fun `6 exact bounded compiler-submission counting`() {
        val c1 = FakeTestCompiler("c1")
        val c2 = FakeTestCompiler("c2")
        val diffTargets = listOf(CompilerTarget("c1", c1, Language.KOTLIN), CompilerTarget("c2", c2, Language.KOTLIN))

        val runner = TestableRunner(
            diffList = diffTargets,
            customProgram = buildProgram { },
        )
        runner.runWithArgs("-m", "diff", "--max-rounds", "4")

        assertEquals(4, c1.invocations.size + c2.invocations.size)
        assertEquals(2, c1.invocations.size)
        assertEquals(2, c2.invocations.size)
        assertEquals(4, runner.budget.submittedInvocations)
        assertEquals(4, runner.budget.completedInvocations)
    }

    @Test
    fun `normal mode generates independently per target and reserves exactly one invocation`() {
        val c1 = FakeTestCompiler("c1")
        val c2 = FakeTestCompiler("c2")
        val runner = TestableRunner(
            normalList = listOf(
                CompilerTarget("c1", c1, Language.GROOVY4),
                CompilerTarget("c2", c2, Language.GROOVY5),
            ),
            customProgram = buildProgram { },
        )

        runner.runWithArgs("-m", "normal", "--max-rounds", "1")

        assertEquals(1, c1.invocations.size)
        assertEquals(0, c2.invocations.size)
        assertEquals(1, runner.budget.reservedInvocations)
        assertEquals(1, runner.budget.completedInvocations)
        assertEquals(listOf(Language.GROOVY4), runner.generatorLanguages)
    }

    @Test
    fun `normal mode uses each target language when budget reaches both targets`() {
        val c1 = FakeTestCompiler("c1")
        val c2 = FakeTestCompiler("c2")
        val runner = TestableRunner(
            normalList = listOf(
                CompilerTarget("c1", c1, Language.GROOVY4),
                CompilerTarget("c2", c2, Language.GROOVY5),
            ),
            customProgram = buildProgram { },
        )

        runner.runWithArgs("-m", "normal", "--max-rounds", "2")

        assertEquals(listOf(Language.GROOVY4, Language.GROOVY5), runner.generatorLanguages)
        assertEquals(1, c1.invocations.size)
        assertEquals(1, c2.invocations.size)
    }

    @Test
    fun `normal input IR submits targets individually`(@TempDir tempDir: File) {
        val c1 = FakeTestCompiler("c1")
        val c2 = FakeTestCompiler("c2")
        val input = File(tempDir, "main.json")
        input.writer().use { gson.toJson(buildProgram { }, it) }
        val runner = TestableRunner(
            normalList = listOf(
                CompilerTarget("c1", c1, Language.GROOVY4),
                CompilerTarget("c2", c2, Language.GROOVY5),
            ),
        )

        runner.runWithArgs("-m", "normal", "--max-rounds", "1", "-i", input.absolutePath)

        assertEquals(1, c1.invocations.size)
        assertEquals(0, c2.invocations.size)
        assertEquals(1, runner.budget.reservedInvocations)
    }

    @Test
    fun `compiler budget separates submissions from completed invocations`() {
        val compiler = FakeTestCompiler("c1")
        val runner = TestableRunner(
            normalList = listOf(CompilerTarget("c1", compiler, Language.KOTLIN)),
            customProgram = buildProgram { },
        )
        runner.runWithArgs("-m", "normal", "--max-rounds", "1")

        runner.compile(buildProgram { }, listOf(compiler))

        assertEquals(1, runner.budget.submittedInvocations)
        assertEquals(2, runner.budget.completedInvocations)
        assertEquals(2, runner.recorder.getCompileTimes())
        assertEquals(2, compiler.invocations.size)
    }

    @Test
    fun `failed compiler call is submitted but not completed`() {
        val compiler = FakeTestCompiler("throwing") { error("interrupted") }
        val runner = TestableRunner(
            normalList = listOf(CompilerTarget("throwing", compiler, Language.KOTLIN)),
            customProgram = buildProgram { },
        )

        assertThrows<IllegalStateException> {
            runner.runWithArgs("-m", "normal", "--max-rounds", "1")
        }
        assertEquals(1, runner.budget.submittedInvocations)
        assertEquals(0, runner.budget.completedInvocations)
        assertTrue(runner.reportedSummary!!.contains("compiler invocations=0"))
    }

    @Test
    fun `7 stop-on-error behavior`() {
        val c1 = FakeTestCompiler("c1") { CompileResult("c1", "some compiler bug", null) }
        val normalTarget = CompilerTarget("c1", c1, Language.KOTLIN)

        val runner = TestableRunner(
            normalList = listOf(normalTarget),
            customProgram = buildProgram { },
        )
        // max-rounds 100, but stop-on-errors is enabled
        runner.runWithArgs("-m", "normal", "-s", "--max-rounds", "100")

        assertEquals(1, c1.invocations.size, "Should stop immediately after first error")
    }

    @Test
    fun `same runner can run again after stop-on-error`() {
        var shouldFail = true
        val compiler = FakeTestCompiler("c1") {
            if (shouldFail) CompileResult("c1", "failure", null) else CompileResult("c1", null, null)
        }
        val runner = TestableRunner(
            normalList = listOf(CompilerTarget("c1", compiler, Language.KOTLIN)),
            customProgram = buildProgram { },
        )

        runner.runWithArgs("-m", "normal", "-s", "--max-rounds", "1")
        shouldFail = false
        runner.runWithArgs("-m", "normal", "-s", "--max-rounds", "1")

        assertEquals(2, compiler.invocations.size)
        assertEquals(1, runner.recorder.getCompileTimes())
        assertEquals(1, runner.budget.completedInvocations)
    }

    @Test
    fun `8 differential target isolation via independent program copies`() {
        lateinit var programInTarget1: IrProgram
        lateinit var programInTarget2: IrProgram

        val c1 = FakeTestCompiler("c1") { prog ->
            programInTarget1 = prog
            CompileResult("c1", null, null)
        }
        val c2 = FakeTestCompiler("c2") { prog ->
            programInTarget2 = prog
            CompileResult("c2", null, null)
        }
        val diffTargets = listOf(CompilerTarget("c1", c1, Language.KOTLIN), CompilerTarget("c2", c2, Language.KOTLIN))

        val originalProg = buildProgram { }
        val runner = TestableRunner(
            diffList = diffTargets,
            customProgram = originalProg,
        )
        runner.runWithArgs("-m", "diff", "--max-rounds", "2")

        assertNotSame(originalProg, programInTarget1)
        assertNotSame(originalProg, programInTarget2)
        assertNotSame(programInTarget1, programInTarget2, "Each target must receive an isolated deep copy")
    }

    @Test
    fun `9 compiler-specific major-language preparation`() {
        var lang1: Language? = null
        var lang2: Language? = null

        val c1 = FakeTestCompiler("c1") { prog ->
            lang1 = prog.classes.firstOrNull()?.language
            CompileResult("c1", null, null)
        }
        val c2 = FakeTestCompiler("c2") { prog ->
            lang2 = prog.classes.firstOrNull()?.language
            CompileResult("c2", null, null)
        }
        val diffTargets = listOf(
            CompilerTarget("c1", c1, Language.GROOVY4),
            CompilerTarget("c2", c2, Language.GROOVY5),
        )

        val progWithClass = buildProgram {
            classes += buildClassDeclaration {
                name = "Foo"
                classKind = ClassKind.FINAL
            }
        }

        val runner = TestableRunner(
            runnerLanguage = Language.GROOVY4,
            diffList = diffTargets,
            customProgram = progWithClass,
        )
        runner.runWithArgs("-m", "diff", "--max-rounds", "2")

        assertEquals(Language.GROOVY4, lang1)
        assertEquals(Language.GROOVY5, lang2)
    }

    @Test
    fun `10 minimization and recording only for interesting results`(@TempDir tempDir: File) {
        val minOutDir = File(tempDir, "min-out").apply { mkdirs() }
        File(minOutDir, "historical-report").mkdirs()
        val c1 = FakeTestCompiler("c1") { CompileResult("c1", null, null) }
        val c2 = FakeTestCompiler("c2") { CompileResult("c2", "failure", null) }
        val diffTargets = listOf(CompilerTarget("c1", c1, Language.KOTLIN), CompilerTarget("c2", c2, Language.KOTLIN))

        val runner = TestableRunner(
            diffList = diffTargets,
            customProgram = buildProgram { },
        )
        runner.runWithArgs("-m", "diff", "--max-rounds", "2", "-nso", minOutDir.absolutePath)

        assertTrue(minOutDir.exists())
        val recorded = minOutDir.listFiles() ?: emptyArray()
        assertEquals(2, recorded.size, "Historical and current differential reports should remain on disk")
        assertTrue(runner.reportedSummary!!.contains("differential reports=1"))
    }

    @Test
    fun `11 cross-language java classes preserved during preparation`() {
        lateinit var recordedProg: IrProgram
        val c1 = FakeTestCompiler("c1") { prog ->
            recordedProg = prog
            CompileResult("c1", null, null)
        }
        val target = CompilerTarget("c1", c1, Language.KOTLIN)

        val progWithMixed = buildProgram {
            classes += buildClassDeclaration {
                name = "JavaClass"
                this.language = Language.JAVA
                classKind = ClassKind.FINAL
            }
            classes += buildClassDeclaration {
                name = "KotlinClass"
                this.language = Language.KOTLIN
                classKind = ClassKind.FINAL
            }
        }

        val runner = TestableRunner(
            normalList = listOf(target),
            customProgram = progWithMixed,
        )
        runner.runWithArgs("-m", "normal", "--max-rounds", "1")

        val javaClass = recordedProg.classes.first { it.name == "JavaClass" }
        val ktClass = recordedProg.classes.first { it.name == "KotlinClass" }
        assertEquals(Language.JAVA, javaClass.language, "Java class must remain Java")
        assertEquals(Language.KOTLIN, ktClass.language, "Kotlin class must remain Kotlin")
    }

    @Test
    fun `12 input IR filtering for json files and recursion`(@TempDir tempDir: File) {
        val subDir = File(tempDir, "sub").apply { mkdirs() }
        val topJson = File(tempDir, "top.json").apply { writeText(gson.toJson(buildProgram { })) }
        val topTxt = File(tempDir, "top.txt").apply { writeText("ignore me") }
        val subJson = File(subDir, "nested.JSON").apply { writeText(gson.toJson(buildProgram { })) }

        val compiled = mutableListOf<String>()
        val compiler = FakeTestCompiler("c1") {
            compiled.add("run")
            CompileResult("c1", null, null)
        }
        val target = CompilerTarget("c1", compiler, Language.KOTLIN)

        // Non-recursive: only topJson should be processed
        val nonRecRunner = TestableRunner(normalList = listOf(target))
        nonRecRunner.runWithArgs("-m", "normal", "-i", tempDir.absolutePath)
        assertEquals(1, compiled.size, "Non-recursive should only pick top-level json")

        compiled.clear()
        // Recursive: topJson and subJson (nested.JSON) should be processed, txt ignored
        val recRunner = TestableRunner(normalList = listOf(target))
        recRunner.runWithArgs("-m", "normal", "-i", tempDir.absolutePath, "-ri")
        assertEquals(2, compiled.size, "Recursive should pick both top and nested json files")
    }

    @Test
    fun `13 recordCompileResult forceRecord handles identical compileResults`(@TempDir tempDir: File) {
        val outDir = File(tempDir, "oracle-record")
        val prog = buildProgram { }
        val identicalResults = listOf(
            CompileResult("c1", "compile error", null),
            CompileResult("c2", "compile error", null),
        )
        // In normal diff mode this would fail require(), but forceRecord = true allows oracle mismatch recording
        recordCompileResult(
            majorLanguage = Language.KOTLIN,
            program = prog,
            compileResults = identicalResults,
            outDir = outDir,
            forceRecord = true,
        )
        assertTrue(outDir.exists())
        val recorded = outDir.listFiles() ?: emptyArray()
        assertEquals(1, recorded.size)
    }

    @Test
    fun `14 reportSummary counts directory artifacts and respects termination`(@TempDir tempDir: File) {
        val logDir = File(tempDir, "normal-out")
        val nsoDir = File(tempDir, "diff-out")
        val failingCompiler = FakeTestCompiler("failing") { CompileResult("failing", "syntax error", null) }
        val target = CompilerTarget("failing", failingCompiler, Language.KOTLIN)
        File(logDir, "historical-report").mkdirs()

        val runner = TestableRunner(
            normalList = listOf(target),
            customProgram = buildProgram { },
        )
        runner.runWithArgs(
            "-m", "normal",
            "--max-rounds", "1",
            "-o", logDir.absolutePath,
            "-nso", nsoDir.absolutePath,
        )

        // Verify that report directory has product/artifacts
        assertTrue(logDir.exists(), "Report folder should exist")
        val reports = logDir.listFiles { f -> f.isDirectory } ?: emptyArray()
        assertEquals(2, reports.size, "The historical folder and this run's report should both remain on disk")
        val currentReport = reports.single { it.name != "historical-report" }
        val reportFiles = currentReport.listFiles()?.map { it.name } ?: emptyList()
        assertTrue(reportFiles.any { it.endsWith(".kt") || it.endsWith(".json") }, "Report folder should contain code or IR files")
        assertTrue(runner.reportedSummary!!.contains("raw reports=1"), "Historical directories must not enter this run's summary")
        assertNull(runner.reportSummary(), "Summary must only be emitted once")
    }

    @Test
    fun `generate-only summary reports generated rounds`(@TempDir tempDir: File) {
        val runner = TestableRunner(customProgram = buildProgram { })
        val irOut = File(tempDir, "ir-out")

        runner.runWithArgs("-m", "ironly", "--max-rounds", "3", "-iro", irOut.absolutePath)

        assertEquals(3, irOut.listFiles { file -> file.isDirectory }?.size)
        assertTrue(runner.reportedSummary!!.contains("completed rounds=3"))
        assertTrue(runner.reportedSummary!!.contains("generated IR=3"))
        assertTrue(runner.reportedSummary!!.contains("compiler invocations=0"))
    }

    @Test
    fun `normal mutation mismatch is recorded under requested out directory`(@TempDir tempDir: File) {
        val out = File(tempDir, "requested-out")
        val differentialOut = File(tempDir, "differential-out")
        var calls = 0
        val compiler = FakeTestCompiler("c1") {
            calls++
            if (calls == 1) CompileResult("c1", null, null) else CompileResult("c1", "failure", null)
        }
        val runner = TestableRunner(
            normalList = listOf(CompilerTarget("c1", compiler, Language.KOTLIN)),
            customProgram = buildProgram { },
            mutatorLogic = { _, _ -> SemanticValidity.VALID to "known-valid mutation" },
        )

        runner.runWithArgs(
            "-m", "normal", "-s", "--max-rounds", "2",
            "-o", out.absolutePath,
            "-nso", differentialOut.absolutePath,
        )

        assertEquals(1, out.listFiles { file -> file.isDirectory }?.size)
        assertEquals(0, differentialOut.listFiles { file -> file.isDirectory }?.size ?: 0)
        assertTrue(runner.reportedSummary!!.contains("raw reports=1"))
    }

    @Test
    fun `null mutation retries within the same chain`() {
        val mutations = mutableListOf<Pair<SemanticValidity, String?>?>(
            null,
            SemanticValidity.VALID to "valid",
            null,
            null,
            null,
        )
        val compiler = FakeTestCompiler("c1")
        val runner = TestableRunner(
            normalList = listOf(CompilerTarget("c1", compiler, Language.KOTLIN)),
            customProgram = buildProgram { },
            mutatorLogic = { _, _ -> mutations.removeAt(0) },
        )

        runner.runWithArgs("-m", "normal", "--max-rounds", "2")

        assertSame(runner.mutatedPrograms[0], runner.mutatedPrograms[1])
        assertEquals(2, compiler.invocations.size)
    }

    @Test
    fun `unknown mutation discards the current chain`() {
        val mutations = mutableListOf<Pair<SemanticValidity, String?>?>(
            SemanticValidity.UNKNOWN to "unknown",
            SemanticValidity.VALID to "next program",
        )
        val compiler = FakeTestCompiler("c1")
        val runner = TestableRunner(
            normalList = listOf(CompilerTarget("c1", compiler, Language.KOTLIN)),
            customProgram = buildProgram { },
            mutatorLogic = { _, _ -> mutations.removeAt(0) },
        )

        runner.runWithArgs("-m", "normal", "--max-rounds", "2")

        assertEquals(2, runner.generateCalls)
        assertNotSame(runner.mutatedPrograms[0], runner.mutatedPrograms[1])
        assertEquals(2, compiler.invocations.size)
    }

    @Test
    fun `valid mutation continues while invalid mutation stops its chain`() {
        val validMutations = mutableListOf<Pair<SemanticValidity, String?>?>(
            SemanticValidity.VALID to "valid-1",
            SemanticValidity.VALID to "valid-2",
            null,
            null,
            null,
        )
        val validCompiler = FakeTestCompiler("valid")
        val validRunner = TestableRunner(
            normalList = listOf(CompilerTarget("valid", validCompiler, Language.KOTLIN)),
            customProgram = buildProgram { },
            mutatorLogic = { _, _ -> validMutations.removeAt(0) },
        )
        validRunner.runWithArgs("-m", "normal", "--max-rounds", "3")
        assertSame(validRunner.mutatedPrograms[0], validRunner.mutatedPrograms[1])
        assertEquals(3, validCompiler.invocations.size)

        val invalidMutations = mutableListOf<Pair<SemanticValidity, String?>?>(
            SemanticValidity.INVALID to "invalid",
            SemanticValidity.VALID to "next program",
        )
        var invalidCompileCalls = 0
        val invalidCompiler = FakeTestCompiler("invalid") {
            invalidCompileCalls++
            if (invalidCompileCalls == 2) CompileResult("invalid", "expected failure", null)
            else CompileResult("invalid", null, null)
        }
        val invalidRunner = TestableRunner(
            normalList = listOf(CompilerTarget("invalid", invalidCompiler, Language.KOTLIN)),
            customProgram = buildProgram { },
            mutatorLogic = { _, _ -> invalidMutations.removeAt(0) },
        )
        invalidRunner.runWithArgs("-m", "normal", "--max-rounds", "3")
        assertEquals(2, invalidRunner.generateCalls)
        assertNotSame(invalidRunner.mutatedPrograms[0], invalidRunner.mutatedPrograms[1])
        assertEquals(3, invalidCompiler.invocations.size)
    }
}
