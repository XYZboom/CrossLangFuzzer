package com.github.xyzboom.codesmith.kotlin

import org.jetbrains.kotlin.test.TestJdkKind
import org.jetbrains.kotlin.test.services.KotlinTestInfo
import java.io.File

interface IDifferentialTest {
    val jdk: TestJdkKind
    fun testProgram(fileContent: String): KotlinCompileResult {
        val tempFile = File.createTempFile("code-smith", ".kt")
        tempFile.writeText("// JDK_KIND: ${jdk.name}\n$fileContent")
        try {
            runTest(tempFile.absolutePath)
        } catch (e: Throwable) {
            return KotlinCompileResult(e, fileContent, this::class.simpleName!!)
        }
        return KotlinCompileResult(null, fileContent, this::class.simpleName!!)
    }

    fun runTest(filePath: String)
    fun initTestInfo(testInfo: KotlinTestInfo)

    interface IJDK8Test: IDifferentialTest {
        override val jdk: TestJdkKind get() = TestJdkKind.FULL_JDK
    }

    interface IJDK17Test: IDifferentialTest {
        override val jdk: TestJdkKind get() = TestJdkKind.FULL_JDK_17
    }
}