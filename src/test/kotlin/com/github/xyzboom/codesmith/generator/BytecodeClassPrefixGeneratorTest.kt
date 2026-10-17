package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.config.RunConfig
import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.builder.buildModule
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.classes
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.partitionClassesToModules
import com.github.xyzboom.codesmith.ir.types.type
import com.github.xyzboom.codesmith.serde.configGson
import com.github.xyzboom.codesmith.serde.gson
import com.google.gson.JsonParseException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.random.Random

class BytecodeClassPrefixGeneratorTest {
    @Test
    fun `tracked bytecode config parses and configures moduleNumRange`() {
        val config = File("config/default-kotlin-bytecode.json").reader().use {
            configGson.fromJson(it, RunConfig::class.java)
        }
        assertEquals(2..4, config.generatorConfig.moduleNumRange)
    }

    @Test
    fun `generator executes module partitioning as last step`() {
        val generator = IrDeclGenerator(
            config = GeneratorConfig(
                topLevelDeclRange = 4..6,
                moduleNumRange = 2..3,
                javaRatio = 0.5f,
            ),
            random = Random(42),
            majorLanguage = Language.KOTLIN,
        )
        val program = generator.genProgram()
        assertTrue(program.modules.isNotEmpty())
        assertTrue(program.modules.size in 1..3)
        assertEquals("main", program.modules.last().name)
        assertEquals(program.classes.size, program.modules.sumOf { it.classes.size })
    }

    @Test
    fun `partitionClassesToModules preserves SCC and establishes dependencies`() {
        val base = buildClassDeclaration {
            name = "Base"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
        }
        val derived = buildClassDeclaration {
            name = "Derived"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            superType = base.type
        }
        val program = partitionClassesToModules(listOf(base, derived), moduleNumRange = 2..2, random = Random(1))
        assertEquals(2, program.modules.size)
        val m0 = program.modules[0]
        val main = program.modules[1]
        assertEquals("m0", m0.name)
        assertEquals("main", main.name)
        assertTrue(m0.classes.contains(base))
        assertTrue(main.classes.contains(derived))
        assertTrue(main.dependencies.contains(m0))
    }

    @Test
    fun `serde roundtrip preserves modules and inter-module dependencies`() {
        val base = buildClassDeclaration {
            name = "Base"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
        }
        val derived = buildClassDeclaration {
            name = "Derived"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            superType = base.type
        }
        val m0 = buildModule {
            name = "m0"
            classes += base
        }
        val main = buildModule {
            name = "main"
            dependencies += m0
            classes += derived
        }
        val original = buildProgram {
            modules += m0
            modules += main
        }

        val json = gson.toJson(original)
        val deserialized = gson.fromJson(json, com.github.xyzboom.codesmith.ir.IrProgram::class.java)

        assertEquals(2, deserialized.modules.size)
        assertEquals("m0", deserialized.modules[0].name)
        assertEquals("main", deserialized.modules[1].name)
        assertEquals(listOf("Base"), deserialized.modules[0].classes.map { it.name })
        assertEquals(listOf("Derived"), deserialized.modules[1].classes.map { it.name })
        assertEquals(listOf("m0"), deserialized.modules[1].dependencies.map { it.name })
    }

    @Test
    fun `deserializer maintains compatibility with legacy unmodularized JSON`() {
        val base = buildClassDeclaration {
            name = "LegacyBase"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
        }
        val json = """{
            "@type": "IrProgram",
            "classes": [
                ${gson.toJson(base)}
            ]
        }"""
        val deserialized = gson.fromJson(json, com.github.xyzboom.codesmith.ir.IrProgram::class.java)
        assertEquals(1, deserialized.modules.size)
        assertEquals("main", deserialized.modules[0].name)
        assertEquals(listOf("LegacyBase"), deserialized.modules[0].classes.map { it.name })
    }

    @Test
    fun `deserializer rejects invalid JSON without modules or classes`() {
        val invalidJson = """{"@type": "IrProgram"}"""
        assertThrows<JsonParseException> {
            gson.fromJson(invalidJson, com.github.xyzboom.codesmith.ir.IrProgram::class.java)
        }
    }
}
