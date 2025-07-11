package com.github.xyzboom.codesmith.kotlin

import com.github.xyzboom.codesmith.CompileResult
import org.jetbrains.kotlin.test.TestJdkKind
import org.jetbrains.kotlin.test.services.KotlinTestInfo
import java.io.File

interface IKotlinCompilerTest {
    val jdk: TestJdkKind
    fun testProgram(fileContent: String): CompileResult {
        val tempFile = File.createTempFile("code-smith", ".kt")
        tempFile.writeText("// JDK_KIND: ${jdk.name}\n$fileContent")
        val e: Throwable? = try {
            runTest(tempFile.absolutePath)
            null
        } catch (e: Throwable) {
            e
        }
        return toCompileResult(this::class.simpleName!!, e)
    }

    fun runTest(filePath: String)
    fun initTestInfo(testInfo: KotlinTestInfo)

    interface IJDK8Test: IKotlinCompilerTest {
        override val jdk: TestJdkKind get() = TestJdkKind.FULL_JDK
    }

    interface IJDK17Test: IKotlinCompilerTest {
        override val jdk: TestJdkKind get() = TestJdkKind.FULL_JDK_17
    }
}