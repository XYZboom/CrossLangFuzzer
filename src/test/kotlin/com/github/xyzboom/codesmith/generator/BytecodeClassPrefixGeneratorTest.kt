package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.config.RunConfig
import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.Visibility
import com.github.xyzboom.codesmith.ir.builder.buildModule
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.classes
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.partitionClassesToModules
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
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
        assertEquals(0.25f, config.generatorConfig.nestedClassProbability)
        assertEquals(0.3f, config.generatorConfig.nestedTypeAsSuperProbability)
        assertEquals(6, config.generatorConfig.functionPublicVisibilityWeight)
        assertEquals(2, config.generatorConfig.functionProtectedVisibilityWeight)
        assertEquals(0.5f, config.generatorConfig.overrideVisibilityWidenProbability)
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
    fun `generator emits nested declarations and language-legal visibility values`() {
        val program = IrDeclGenerator(
            config = GeneratorConfig(
                topLevelDeclRange = 4..4,
                nestedClassProbability = 1f,
                nestedClassNumRange = 1..1,
                moduleNumRange = 1..1,
            ),
            random = Random(71),
            majorLanguage = Language.KOTLIN,
        ).genProgram()
        val nested = program.classes.flatMap { it.classes }
        val allClasses = program.classes + nested
        assertEquals(program.classes.size, nested.size)
        assertEquals(allClasses.size, allClasses.map { it.name }.toSet().size, "IR references use global class names")
        assertTrue(nested.all { it.language == it.containerClassLanguage(program.classes) })
        assertTrue(allClasses.all { clazz ->
            when (clazz.language) {
                Language.KOTLIN -> clazz.visibility != Visibility.PACKAGE_PRIVATE
                Language.JAVA -> clazz.visibility != Visibility.INTERNAL
                else -> clazz.visibility == Visibility.PUBLIC
            }
        })
        val generatedVisibilities = (program.classes + nested).map { it.visibility } +
            program.classes.flatMap { it.functions.map { function -> function.visibility } }
        assertTrue(generatedVisibilities.distinct().size >= 2, generatedVisibilities.toString())
    }

    @Test
    fun `method visibility uses weighted source-language modifiers`() {
        val program = IrDeclGenerator(
            config = GeneratorConfig(
                topLevelDeclRange = 80..80,
                classMemberNumRange = 8..8,
                classHasTypeParameterProbability = 0f,
                functionHasTypeParameterProbability = 0f,
                functionParameterNumRange = 0..0,
                nestedClassProbability = 0f,
                nestedTypeAsSuperProbability = 0f,
                moduleNumRange = 1..1,
            ),
            random = Random(590),
            majorLanguage = Language.KOTLIN,
        ).genProgram()
        val methods = program.classes
            .filter { it.classKind != ClassKind.INTERFACE }
            .flatMap { it.functions }
            .filter { it.body != null && !it.isOverride }

        for ((language, languageSpecific) in listOf(
            Language.JAVA to Visibility.PACKAGE_PRIVATE,
            Language.KOTLIN to Visibility.INTERNAL,
        )) {
            val counts = methods.filter { it.language == language }.groupingBy { it.visibility }.eachCount()
            assertTrue(counts.values.sum() > 100, "too few $language method samples: $counts")
            assertTrue((counts[Visibility.PUBLIC] ?: 0) > 0, "$language public missing: $counts")
            assertTrue((counts[Visibility.PROTECTED] ?: 0) > 0, "$language protected missing: $counts")
            assertTrue((counts[Visibility.PRIVATE] ?: 0) > 0, "$language private missing: $counts")
            assertTrue((counts[languageSpecific] ?: 0) > 0, "$language-specific visibility missing: $counts")
            assertTrue((counts[Visibility.PUBLIC] ?: 0) > (counts[Visibility.PROTECTED] ?: 0), "$language ratio: $counts")
        }
    }

    @Test
    fun `overrides widen protected visibility with configured probability`() {
        val config = GeneratorConfig(
            topLevelDeclRange = 120..120,
            classMemberNumRange = 5..5,
            classHasTypeParameterProbability = 0f,
            functionHasTypeParameterProbability = 0f,
            functionParameterNumRange = 0..0,
            functionPublicVisibilityWeight = 0,
            functionProtectedVisibilityWeight = 1,
            functionPackagePrivateVisibilityWeight = 0,
            functionInternalVisibilityWeight = 0,
            functionPrivateVisibilityWeight = 0,
            overrideVisibilityWidenProbability = 0.5f,
            nestedClassProbability = 0f,
            nestedTypeAsSuperProbability = 0f,
            noFinalFunction = true,
            moduleNumRange = 1..1,
        )
        val program = IrDeclGenerator(config, Random(381), Language.KOTLIN).genProgram()
        val overrides = program.classes.flatMap { it.functions }.filter { function ->
            function.isOverride && function.override.isNotEmpty() &&
                function.override.all { it.visibility == Visibility.PROTECTED }
        }
        assertTrue(overrides.size >= 10, "too few protected-parent overrides: ${overrides.size}")
        val widened = overrides.count { it.visibility == Visibility.PUBLIC }
        val preserved = overrides.count { it.visibility == Visibility.PROTECTED }
        val widenedRatio = widened.toDouble() / overrides.size
        assertTrue(widened > 0 && preserved > 0, "widened=$widened preserved=$preserved")
        assertTrue(widenedRatio in 0.25..0.75, "expected roughly 50% widening, got $widenedRatio")
    }

    @Test
    fun `nested class visibility is sampled across non-interface class kinds`() {
        val program = IrDeclGenerator(
            config = GeneratorConfig(
                topLevelDeclRange = 120..120,
                javaRatio = 0.5f,
                classMemberNumRange = 0..0,
                classImplNumRange = 0..0,
                classHasTypeParameterProbability = 0f,
                nestedClassProbability = 1f,
                nestedClassNumRange = 2..2,
                nestedTypeAsSuperProbability = 0f,
                moduleNumRange = 1..1,
            ),
            random = Random(20261101),
            majorLanguage = Language.KOTLIN,
        ).genProgram()
        assertTrue(program.classes.all { it.visibility == Visibility.PUBLIC })
        for ((language, languageSpecific) in listOf(
            Language.JAVA to Visibility.PACKAGE_PRIVATE,
            Language.KOTLIN to Visibility.INTERNAL,
        )) {
            val nested = program.classes
                .filter { it.classKind != ClassKind.INTERFACE && it.language == language }
                .flatMap { it.classes }
            val visibilities = nested.map { it.visibility }.toSet()
            assertTrue(Visibility.PUBLIC in visibilities, "$language public missing: $visibilities")
            assertTrue(Visibility.PROTECTED in visibilities, "$language protected missing: $visibilities")
            assertTrue(Visibility.PRIVATE in visibilities, "$language private missing: $visibilities")
            assertTrue(languageSpecific in visibilities, "$languageSpecific missing for $language: $visibilities")
        }
    }

    @Test
    fun `Java package-private overrides may widen to protected or public`() {
        val program = IrDeclGenerator(
            config = GeneratorConfig(
                topLevelDeclRange = 240..240,
                javaRatio = 1f,
                classMemberNumRange = 5..5,
                classHasTypeParameterProbability = 0f,
                functionHasTypeParameterProbability = 0f,
                functionParameterNumRange = 0..0,
                functionPublicVisibilityWeight = 0,
                functionProtectedVisibilityWeight = 0,
                functionPackagePrivateVisibilityWeight = 1,
                functionInternalVisibilityWeight = 1,
                functionPrivateVisibilityWeight = 0,
                overrideVisibilityWidenProbability = 0.75f,
                nestedClassProbability = 0f,
                nestedTypeAsSuperProbability = 0f,
                noFinalFunction = true,
                moduleNumRange = 1..1,
            ),
            random = Random(20261102),
            majorLanguage = Language.JAVA,
        ).genProgram()
        val overrides = program.classes.flatMap { it.functions }.filter { function ->
            function.isOverride && !function.isOverrideStub && function.override.isNotEmpty() &&
                function.override.all { it.visibility == Visibility.PACKAGE_PRIVATE }
        }
        val counts = overrides.groupingBy { it.visibility }.eachCount()
        assertTrue(overrides.size >= 10, "too few package-private-parent overrides: $counts")
        assertTrue((counts[Visibility.PACKAGE_PRIVATE] ?: 0) > 0, "preserved package-private missing: $counts")
        assertTrue((counts[Visibility.PROTECTED] ?: 0) > 0, "protected widening missing: $counts")
        assertTrue((counts[Visibility.PUBLIC] ?: 0) > 0, "public widening missing: $counts")
    }

    @Test
    fun `Kotlin internal overrides crossing modules widen both declarations`() {
        val program = IrDeclGenerator(
            config = GeneratorConfig(
                topLevelDeclRange = 100..100,
                javaRatio = 0f,
                classMemberNumRange = 5..5,
                classHasTypeParameterProbability = 0f,
                functionHasTypeParameterProbability = 0f,
                functionParameterNumRange = 0..0,
                functionPublicVisibilityWeight = 0,
                functionProtectedVisibilityWeight = 0,
                functionPackagePrivateVisibilityWeight = 1,
                functionInternalVisibilityWeight = 1,
                functionPrivateVisibilityWeight = 0,
                overrideVisibilityWidenProbability = 0f,
                nestedClassProbability = 0f,
                nestedTypeAsSuperProbability = 0f,
                noFinalFunction = true,
                moduleNumRange = 2..3,
            ),
            random = Random(20261103),
            majorLanguage = Language.KOTLIN,
        ).genProgram()
        val classToModule = program.modules
            .flatMap { module -> module.classes.map { clazz -> clazz.name to module.name } }
            .toMap()
        val crossModuleOverrides = program.classes.flatMap { child ->
            child.functions.flatMap { function ->
                function.override.filter { parent ->
                    classToModule[parent.containingClassName] != classToModule[child.name]
                }.map { parent -> function to parent }
            }
        }
        assertTrue(crossModuleOverrides.isNotEmpty(), "expected at least one cross-module override")
        assertTrue(crossModuleOverrides.all { (child, parent) ->
            child.visibility == Visibility.PUBLIC && parent.visibility == Visibility.PUBLIC
        }) {
            crossModuleOverrides.joinToString { (child, parent) ->
                "${child.containingClassName}.${child.name}=${child.visibility} <- ${parent.containingClassName}.${parent.name}=${parent.visibility}"
            }
        }
    }

    @Test
    fun `Scala nested inheritance stays disabled until nested type syntax is supported`() {
        val program = IrDeclGenerator(
            config = GeneratorConfig(
                topLevelDeclRange = 12..12,
                javaRatio = 0f,
                classImplNumRange = 0..0,
                classMemberNumRange = 0..0,
                classHasTypeParameterProbability = 0f,
                nestedClassProbability = 1f,
                nestedClassNumRange = 2..2,
                nestedTypeAsSuperProbability = 1f,
                moduleNumRange = 1..1,
            ),
            random = Random(20261031),
            majorLanguage = Language.SCALA,
        ).genProgram()
        val nested = program.classes.flatMap { it.classes }.toSet()
        val nestedSuperReferences = (program.classes + program.classes.flatMap { it.classes })
            .flatMap { clazz -> listOfNotNull(clazz.superType) + clazz.implementedTypes }
            .filterIsInstance<com.github.xyzboom.codesmith.ir.types.IrClassifier>()
            .filter { it.classDecl in nested }
        assertTrue(nestedSuperReferences.isEmpty(), nestedSuperReferences.toString())
    }

    @Test
    fun `nested visibility is printed inside its enclosing source class`() {
        val nested = buildClassDeclaration {
            name = "Nested"
            language = Language.KOTLIN
            visibility = Visibility.PRIVATE
            classKind = ClassKind.FINAL
        }
        val outer = buildClassDeclaration {
            name = "Outer"
            language = Language.KOTLIN
            visibility = Visibility.PUBLIC
            classKind = ClassKind.FINAL
            classes += nested
        }
        val program = partitionClassesToModules(listOf(outer), 1..1, Random(2))
        val json = gson.toJson(program)
        val roundTrip = gson.fromJson(json, com.github.xyzboom.codesmith.ir.IrProgram::class.java)
        val restoredOuter = roundTrip.classes.single()
        assertEquals(Visibility.PUBLIC, restoredOuter.visibility)
        assertEquals(Visibility.PRIVATE, restoredOuter.classes.single().visibility)
        val source = IrProgramPrinter(majorLanguage = Language.KOTLIN).print(roundTrip).getValue("Outer.kt")
        assertTrue("private class Nested" in source, source)
    }

    @Test
    fun `deserializer rejects a nested name that collides with a top-level class`() {
        val topLevel = buildClassDeclaration {
            name = "Shared"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
        }
        val nested = buildClassDeclaration {
            name = "Shared"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
        }
        val outer = buildClassDeclaration {
            name = "Outer"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            classes += nested
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += topLevel
                classes += outer
            }
        }

        val exception = assertThrows<JsonParseException> {
            gson.fromJson(gson.toJson(program), com.github.xyzboom.codesmith.ir.IrProgram::class.java)
        }

        assertTrue(exception.message.orEmpty().contains("Duplicate class name 'Shared'"), exception.message)
    }

    @Test
    fun `deserializer rejects duplicate nested names instead of merging class identities`() {
        fun nested(name: String) = buildClassDeclaration {
            this.name = name
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
        }
        val firstOuter = buildClassDeclaration {
            name = "FirstOuter"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            classes += nested("Nested")
        }
        val secondOuter = buildClassDeclaration {
            name = "SecondOuter"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            classes += nested("Nested")
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += firstOuter
                classes += secondOuter
            }
        }

        val exception = assertThrows<JsonParseException> {
            gson.fromJson(gson.toJson(program), com.github.xyzboom.codesmith.ir.IrProgram::class.java)
        }
        assertTrue(exception.message.orEmpty().contains("Duplicate class name 'Nested'"), exception.message)
    }

    private fun IrClassDeclaration.containerClassLanguage(roots: List<IrClassDeclaration>): Language? =
        roots.firstOrNull { this in it.classes }?.language

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
    fun `partitionClassesToModules includes transitive module dependencies`() {
        val base = buildClassDeclaration {
            name = "Base"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
        }
        val middle = buildClassDeclaration {
            name = "Middle"
            language = Language.KOTLIN
            classKind = ClassKind.OPEN
            superType = base.type
        }
        val derived = buildClassDeclaration {
            name = "Derived"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            superType = middle.type
        }

        val program = partitionClassesToModules(
            listOf(base, middle, derived),
            moduleNumRange = 3..3,
            random = Random(1),
        )

        assertEquals(listOf("m0", "m1"), program.modules.single { it.name == "main" }.dependencies.map { it.name })
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
