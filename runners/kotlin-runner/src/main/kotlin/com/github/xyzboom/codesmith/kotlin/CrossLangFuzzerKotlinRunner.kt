package com.github.xyzboom.codesmith.kotlin

import com.github.ajalt.clikt.core.main
import com.github.xyzboom.codesmith.CommonCompilerRunner
import com.github.xyzboom.codesmith.CompilerTarget
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.mutator.MutatorLanguageSemantics
import com.github.xyzboom.codesmith.mutator.SemanticValidity
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.runners.codegen.AbstractFirPsiBlackBoxCodegenTest
import org.jetbrains.kotlin.test.services.EnvironmentBasedStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.KotlinTestInfo
import org.jetbrains.kotlin.test.util.KtTestUtil
import java.io.File

private fun findRuntimeJar(classpathJars: List<File>, jarName: String): File {
    val prefix = "$jarName-"
    val candidates = classpathJars.filter { file ->
        file.isFile &&
                file.extension == "jar" &&
                file.name.startsWith(prefix) &&
                file.name.removePrefix(prefix).firstOrNull()?.isDigit() == true &&
                !file.name.endsWith("-sources.jar") &&
                !file.name.endsWith("-javadoc.jar")
    }
    require(candidates.size == 1) {
        "Expected exactly one runtime jar for $jarName, found: ${candidates.joinToString { it.name }}"
    }
    return candidates.single()
}

val testInfo = run {
    if (System.getProperty("JDK_17_0") == null && System.getenv("JDK_17_0") == null &&
        System.getProperty("JDK_17") == null && System.getenv("JDK_17") == null
    ) {
        val javaHome = System.getProperty("java.home")
        if (javaHome != null && Runtime.version().feature() == 17) {
            System.setProperty("JDK_17_0", javaHome)
        }
    }
    if (System.getProperty("idea.home.path") == null) {
        System.setProperty("idea.home.path", System.getProperty("user.dir") ?: ".")
    }
    if (System.getProperty("idea.ignore.disabled.plugins") == null) {
        System.setProperty("idea.ignore.disabled.plugins", "true")
    }
    val classpathJars = System.getProperty("java.class.path")
        .split(File.pathSeparator)
        .map(::File)
    mapOf(
        "org.jetbrains.kotlin.test.kotlin-stdlib" to "kotlin-stdlib",
        "kotlin.full.stdlib.path" to "kotlin-stdlib",
        "org.jetbrains.kotlin.test.kotlin-stdlib-jdk8" to "kotlin-stdlib-jdk8",
        "org.jetbrains.kotlin.test.kotlin-reflect" to "kotlin-reflect",
        "kotlin.reflect.jar.path" to "kotlin-reflect",
        "org.jetbrains.kotlin.test.kotlin-test" to "kotlin-test",
        "org.jetbrains.kotlin.test.kotlin-script-runtime" to "kotlin-script-runtime",
        "org.jetbrains.kotlin.test.kotlin-annotations-jvm" to "kotlin-annotations-jvm",
    ).forEach { (property, jarName) ->
        if (System.getProperty(property) == null) {
            System.setProperty(property, findRuntimeJar(classpathJars, jarName).absolutePath)
        }
    }
    // make sure the JDK used by the active compiler matrix is available
    KtTestUtil.getJdk17Home()
    KotlinTestInfo("CrossLangFuzzerKotlinRunner", "main", emptySet())
}

class CrossLangFuzzerKotlinRunner : CommonCompilerRunner("kotlin") {
    companion object {
        fun TestConfigurationBuilder.config() {
            /*
             * Containers of different directives, which can be used in tests:
             * - ModuleStructureDirectives
             * - LanguageSettingsDirectives
             * - DiagnosticsDirectives
             * - FirDiagnosticsDirectives
             * - CodegenTestDirectives
             * - JvmEnvironmentConfigurationDirectives
             *
             * All of them are located in `org.jetbrains.kotlin.test.directives` package
             */
            defaultDirectives {
                +CodegenTestDirectives.IGNORE_DEXING // Avoids loading R8 from the classpath.
            }
        }
    }

    object K2Jdk17Compiler : AbstractFirPsiBlackBoxCodegenTest(), IKotlinCompiler.IJDK17Compiler {
        init {
            initTestInfo(testInfo)
        }

        override fun createKotlinStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider {
            return EnvironmentBasedStandardLibrariesPathProvider
        }

        override fun configure(builder: TestConfigurationBuilder) {
            super.configure(builder)
            builder.config()
        }
    }

    override val runnerLanguage: Language get() = Language.KOTLIN
    override val mutatorLanguageSemantics: MutatorLanguageSemantics get() = MutatorLanguageSemantics.KOTLIN

    override fun normalTargets(): List<CompilerTarget> = listOf(
        CompilerTarget("k2-jdk17", K2Jdk17Compiler, Language.KOTLIN)
    )

    override fun differentialTargets(): List<CompilerTarget> = listOf(
        CompilerTarget("k2-jdk17", K2Jdk17Compiler, Language.KOTLIN)
    )
}

fun isMutationOracleSatisfied(
    semanticValidity: SemanticValidity,
    compileSuccess: Boolean,
): Boolean = com.github.xyzboom.codesmith.isMutationOracleSatisfied(semanticValidity, compileSuccess)

fun main(args: Array<String>) {
    CrossLangFuzzerKotlinRunner().main(args)
}
