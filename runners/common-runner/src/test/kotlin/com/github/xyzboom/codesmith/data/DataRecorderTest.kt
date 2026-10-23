package com.github.xyzboom.codesmith.data

import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import com.github.xyzboom.codesmith.printer.IrProgramPrinter.Companion.extraSourceFileNames
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DataRecorderTest {
    @Test
    fun `line count excludes generated harness and support files`() {
        val program = buildProgram {}
        val sourceFiles = IrProgramPrinter(printStub = false).print(program)
        val programSourceFiles = sourceFiles.filterKeys { it !in extraSourceFileNames }
        val expectedLineCount = programSourceFiles.values.sumOf { it.lines().count() }

        assertTrue(sourceFiles.keys.any { it in extraSourceFileNames })
        assertTrue(sourceFiles.values.sumOf { it.lines().count() } > expectedLineCount)

        val recorder = DataRecorder()
        recorder.addProgram("empty", program)

        assertEquals(expectedLineCount, recorder.programData.getValue("empty").lineOfCode)
    }
}
