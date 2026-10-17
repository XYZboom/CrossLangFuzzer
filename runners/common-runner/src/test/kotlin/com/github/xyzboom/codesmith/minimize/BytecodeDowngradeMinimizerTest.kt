package com.github.xyzboom.codesmith.minimize

import com.github.xyzboom.codesmith.CompileResult
import com.github.xyzboom.codesmith.ICompiler
import com.github.xyzboom.codesmith.ICompilerRunner
import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.builder.buildModule
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.classes
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BytecodeDowngradeMinimizerTest {
    @Test
    fun testDowngradeModules() {
        val m0 = buildModule {
            name = "m0"
            classes.add(buildClassDeclaration { name = "C0"; language = Language.KOTLIN; classKind = ClassKind.FINAL })
        }
        val m1 = buildModule {
            name = "m1"
            classes.add(buildClassDeclaration { name = "C1"; language = Language.KOTLIN; classKind = ClassKind.FINAL })
            dependencies.add(m0)
        }
        val program = buildProgram {
            modules.add(m0)
            modules.add(m1)
        }

        val minimizer = BytecodeDowngradeMinimizer()
        minimizer.tryDowngrade(program) shouldBe true
        program.modules.size shouldBe 1
        program.modules[0].name shouldBe "m1"
        program.classes.map { it.name } shouldBe listOf("C0", "C1")
        minimizer.tryDowngrade(program) shouldBe false
    }

    @Test
    fun testDowngradesModulesWhenBugStillReproduces() {
        val m0 = buildModule {
            name = "m0"
            classes.add(buildClassDeclaration { name = "Parent"; language = Language.JAVA; classKind = ClassKind.FINAL })
        }
        val m1 = buildModule {
            name = "m1"
            classes.add(buildClassDeclaration { name = "Child"; language = Language.KOTLIN; classKind = ClassKind.FINAL })
            dependencies.add(m0)
        }
        val program = buildProgram {
            modules.add(m0)
            modules.add(m1)
        }
        val runner = object : ICompilerRunner {
            override fun compile(program: com.github.xyzboom.codesmith.ir.IrProgram, compilers: List<ICompiler>): List<CompileResult> {
                return listOf(CompileResult("mock", "FAIL BUG", null))
            }
        }
        val origResult = listOf(CompileResult("mock", "FAIL BUG", null))

        val minimizer = BytecodeDowngradeMinimizer()
        val downgraded = minimizer.tryDowngrade(runner, program, origResult, emptyList())
        downgraded.modules.size shouldBe 1
        downgraded.classes.map { it.name } shouldBe listOf("Parent", "Child")
    }

    @Test
    fun testKeepsModulesWhenBugDisappearsOnDowngrade() {
        val m0 = buildModule {
            name = "m0"
            classes.add(buildClassDeclaration { name = "Parent"; language = Language.KOTLIN; classKind = ClassKind.FINAL })
        }
        val m1 = buildModule {
            name = "m1"
            classes.add(buildClassDeclaration { name = "Child"; language = Language.KOTLIN; classKind = ClassKind.FINAL })
            dependencies.add(m0)
        }
        val program = buildProgram {
            modules.add(m0)
            modules.add(m1)
        }
        val runner = object : ICompilerRunner {
            override fun compile(program: com.github.xyzboom.codesmith.ir.IrProgram, compilers: List<ICompiler>): List<CompileResult> {
                val hasMultipleModules = program.modules.size > 1
                return listOf(CompileResult("mock", if (hasMultipleModules) "FAIL BUG" else null, null))
            }
        }
        val origResult = listOf(CompileResult("mock", "FAIL BUG", null))

        val minimizer = BytecodeDowngradeMinimizer()
        val downgraded = minimizer.tryDowngrade(runner, program, origResult, emptyList())
        downgraded.modules.size shouldBe 2
        downgraded.modules.map { it.name } shouldBe listOf("m0", "m1")
    }
}
