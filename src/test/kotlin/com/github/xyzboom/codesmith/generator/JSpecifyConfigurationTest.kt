package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.config.RunConfig
import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.NullabilityAnnotationProvider
import com.github.xyzboom.codesmith.ir.builder.buildModule
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.serde.configGson
import com.github.xyzboom.codesmith.serde.gson
import com.google.gson.JsonParseException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

class JSpecifyConfigurationTest {
    @Test
    fun `JSpecify provider can be selected from JSON config`() {
        val config = configGson.fromJson(
            """{"generatorConfig":{"nullabilityAnnotationProvider":"JSPECIFY"}}""",
            RunConfig::class.java,
        )

        assertEquals(
            NullabilityAnnotationProvider.JSPECIFY,
            config.generatorConfig.nullabilityAnnotationProvider,
        )
    }

    @Test
    fun `default provider remains JetBrains`() {
        assertEquals(
            NullabilityAnnotationProvider.JETBRAINS,
            GeneratorConfig().nullabilityAnnotationProvider,
        )
    }

    @Test
    fun `generator stores provider in program IR after module partitioning`() {
        val generator = IrDeclGenerator(
            config = GeneratorConfig(
                topLevelDeclRange = 1..1,
                moduleNumRange = 1..1,
                nullabilityAnnotationProvider = NullabilityAnnotationProvider.JSPECIFY,
            ),
            random = Random(42),
            majorLanguage = Language.KOTLIN,
        )

        assertEquals(
            NullabilityAnnotationProvider.JSPECIFY,
            generator.genProgram().nullabilityAnnotationProvider,
        )
    }

    @Test
    fun `program serde preserves provider`() {
        val original = buildProgram {
            nullabilityAnnotationProvider = NullabilityAnnotationProvider.JSPECIFY
            modules += buildModule { name = "main" }
        }

        val json = gson.toJson(original)
        val deserialized = gson.fromJson(
            json,
            com.github.xyzboom.codesmith.ir.IrProgram::class.java,
        )

        assertTrue(json.contains("\"nullabilityAnnotationProvider\":\"JSPECIFY\""))
        assertEquals(
            NullabilityAnnotationProvider.JSPECIFY,
            deserialized.nullabilityAnnotationProvider,
        )
    }

    @Test
    fun `legacy program JSON defaults to JetBrains provider`() {
        val javaClass = buildClassDeclaration {
            name = "LegacyJavaClass"
            language = Language.JAVA
            classKind = ClassKind.FINAL
        }
        val json = """{
            "@type": "IrProgram",
            "classes": [${gson.toJson(javaClass)}]
        }"""

        val deserialized = gson.fromJson(
            json,
            com.github.xyzboom.codesmith.ir.IrProgram::class.java,
        )

        assertEquals(
            NullabilityAnnotationProvider.JETBRAINS,
            deserialized.nullabilityAnnotationProvider,
        )
    }

    @Test
    fun `unknown serialized providers fail with a clear parse error`() {
        for (providerName in listOf("UNKNOWN", "JSPECIFY ")) {
            val exception = assertThrows<JsonParseException> {
                gson.fromJson(
                    """{
                        "@type": "IrProgram",
                        "nullabilityAnnotationProvider": "$providerName",
                        "modules": []
                    }""",
                    com.github.xyzboom.codesmith.ir.IrProgram::class.java,
                )
            }

            assertTrue(exception.message!!.contains(providerName))
        }
    }
}
