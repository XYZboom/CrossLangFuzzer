package com.github.xyzboom.codesmith.kotlin

import com.github.xyzboom.codesmith.generator.GeneratorConfig
import com.github.xyzboom.codesmith.generator.IrDeclGenerator
import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.Visibility
import com.github.xyzboom.codesmith.ir.classes
import com.github.xyzboom.codesmith.ir.classDependencyGraph
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.partitionClassesToModules
import com.github.xyzboom.codesmith.ir.types.IrClassifier
import com.github.xyzboom.codesmith.ir.types.type
import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildParameter
import com.github.xyzboom.codesmith.ir.expressions.builder.buildBlock
import com.github.xyzboom.codesmith.printer.IrProgramPrinter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class GeneratedVisibilityCompilerTest {
    @Test
    fun `K2 accepts mixed-language programs with nested declarations and generated visibility`() {
        repeat(5) { seed ->
            val program = IrDeclGenerator(
                config = GeneratorConfig(
                    topLevelDeclRange = 12..12,
                    classMemberNumRange = 2..3,
                    nestedClassProbability = 1f,
                    nestedClassNumRange = 2..2,
                    nestedTypeAsSuperProbability = 1f,
                    javaRatio = 0.5f,
                    moduleNumRange = 2..3,
                ),
                random = Random(seed),
                majorLanguage = Language.KOTLIN,
            ).genProgram()
            assertTrue(program.classes.any { it.classes.isNotEmpty() }, "seed=$seed must exercise nested output")
            assertTrue(program.classes.sumOf { it.functions.size } > 0, "seed=$seed must retain member-function structure")
            val declarations = allClasses(program)
            val nestedDeclarations = declarations.filter { declaration ->
                program.classes.none { it === declaration }
            }.toSet()
            val nestedReferences = declarations.flatMap { declaration ->
                (listOfNotNull(declaration.superType) + declaration.implementedTypes)
                    .filterIsInstance<IrClassifier>()
                    .map { declaration to it.classDecl }
            }.filter { (_, target) -> target in nestedDeclarations }
            assertTrue(nestedReferences.isNotEmpty(), "seed=$seed must inherit from a nested declaration")
            val rootOwner = mutableMapOf<IrClassDeclaration, IrClassDeclaration>()
            fun registerOwner(root: IrClassDeclaration, declaration: IrClassDeclaration) {
                rootOwner[declaration] = root
                declaration.classes.forEach { registerOwner(root, it) }
            }
            program.classes.forEach { registerOwner(it, it) }
            val (usingDeclaration, nestedParent) = nestedReferences.first()
            val usingRoot = rootOwner.getValue(usingDeclaration)
            val parentRoot = rootOwner.getValue(nestedParent)
            assertTrue(usingRoot !== parentRoot, "nested parent must come from a different outer declaration")
            assertTrue(program.classDependencyGraph.getValue(usingRoot).contains(parentRoot), "nested dependency must map to its outer class")
            val usingModule = program.modules.single { usingRoot in it.classes }
            val parentModule = program.modules.single { parentRoot in it.classes }
            assertTrue(usingModule === parentModule || parentModule in usingModule.dependencies, "module dependency must include nested parent owner")
            val source = IrProgramPrinter().printToSingle(program)
            assertTrue("${parentRoot.name}.${nestedParent.name}" in source, "seed=$seed must qualify nested supertype references\\n$source")
            val result = CrossLangFuzzerKotlinRunner.K2Jdk17Compiler.testProgram(source)
            assertTrue(result.success, "seed=$seed\\n$result\\n$source")
        }
    }

    @Test
    fun `K2 accepts Java descendants of Kotlin internal abstract overrides`() {
        val program = IrDeclGenerator(
            config = GeneratorConfig(
                topLevelDeclRange = 12..12,
                javaRatio = 0.5f,
                classMemberNumRange = 1..3,
                classMemberIsFunctionWeight = 3,
                nestedClassProbability = 1f,
                nestedClassNumRange = 2..2,
                nestedTypeAsSuperProbability = 1f,
                noFinalProperties = true,
                moduleNumRange = 1..1,
            ),
            random = Random(20261215),
            majorLanguage = Language.KOTLIN,
        ).genProgram()

        val parent = program.classes.single { it.name == "DV0RxzB" }
        val abstractMember = parent.functions.single { it.name == "cnp" }
        val javaChild = program.classes.single { it.name == "Y7MMn" }
        assertEquals(Language.KOTLIN, parent.language)
        assertEquals(Visibility.INTERNAL, abstractMember.visibility)
        assertTrue(abstractMember.body == null)
        assertEquals(Language.JAVA, javaChild.language)
        assertEquals(ClassKind.ABSTRACT, javaChild.classKind)
        assertTrue((javaChild.superType as IrClassifier).classDecl === parent)

        val source = IrProgramPrinter().printToSingle(program)
        val result = CrossLangFuzzerKotlinRunner.K2Jdk17Compiler.testProgram(source)
        assertTrue(result.success, "$result\\n$source")
    }

    @Test
    fun `K2 accepts cross-language inheritance from a nested type and tracks the outer module dependency`() {
        val nestedBase = buildClassDeclaration {
            name = "NestedBase"
            language = Language.KOTLIN
            visibility = Visibility.PUBLIC
            classKind = ClassKind.OPEN
        }
        val outer = buildClassDeclaration {
            name = "KotlinOuter"
            language = Language.KOTLIN
            visibility = Visibility.PUBLIC
            classKind = ClassKind.FINAL
            classes += nestedBase
        }
        val child = buildClassDeclaration {
            name = "JavaChild"
            language = Language.JAVA
            visibility = Visibility.PUBLIC
            classKind = ClassKind.FINAL
            superType = nestedBase.type
        }
        val program = partitionClassesToModules(
            listOf(outer, child),
            moduleNumRange = 2..2,
            random = Random(9),
        )
        assertTrue(program.classDependencyGraph.getValue(child).contains(outer))
        val childModule = program.modules.single { child in it.classes }
        val outerModule = program.modules.single { outer in it.classes }
        assertTrue(childModule.dependencies.contains(outerModule))

        val source = IrProgramPrinter().printToSingle(program)
        assertTrue("KotlinOuter.NestedBase" in source, source)
        val result = CrossLangFuzzerKotlinRunner.K2Jdk17Compiler.testProgram(source)
        assertTrue(result.success, "$result\\n$source")
    }

    @Test
    fun `K2 compiles recursive nested declarations in Java and Kotlin`() {
        val javaLeaf = buildClassDeclaration {
            name = "JavaLeaf"
            language = Language.JAVA
            visibility = Visibility.PRIVATE
            classKind = ClassKind.FINAL
            functions += function("leafMethod", Language.JAVA, Visibility.PACKAGE_PRIVATE)
        }
        val javaNested = buildClassDeclaration {
            name = "JavaNested"
            language = Language.JAVA
            visibility = Visibility.PROTECTED
            classKind = ClassKind.OPEN
            classes += javaLeaf
        }
        val javaCallableNested = buildClassDeclaration {
            name = "JavaCallableNested"
            language = Language.JAVA
            visibility = Visibility.PUBLIC
            classKind = ClassKind.FINAL
            functions += function("callable", Language.JAVA, Visibility.PUBLIC)
        }
        val javaOuter = buildClassDeclaration {
            name = "JavaOuter"
            language = Language.JAVA
            visibility = Visibility.PUBLIC
            classKind = ClassKind.OPEN
            classes += javaNested
            classes += javaCallableNested
        }

        val kotlinLeaf = buildClassDeclaration {
            name = "KotlinLeaf"
            language = Language.KOTLIN
            visibility = Visibility.PRIVATE
            classKind = ClassKind.FINAL
            functions += function("leafMethod", Language.KOTLIN, Visibility.PROTECTED)
        }
        val kotlinNested = buildClassDeclaration {
            name = "KotlinNested"
            language = Language.KOTLIN
            visibility = Visibility.INTERNAL
            classKind = ClassKind.OPEN
            classes += kotlinLeaf
        }
        val kotlinCallableNested = buildClassDeclaration {
            name = "KotlinCallableNested"
            language = Language.KOTLIN
            visibility = Visibility.PUBLIC
            classKind = ClassKind.FINAL
            functions += function("callable", Language.KOTLIN, Visibility.PUBLIC)
        }
        val kotlinOuter = buildClassDeclaration {
            name = "KotlinOuter"
            language = Language.KOTLIN
            visibility = Visibility.PUBLIC
            classKind = ClassKind.OPEN
            classes += kotlinNested
            classes += kotlinCallableNested
        }
        val program = buildProgram {
            classes += javaOuter
            classes += kotlinOuter
        }

        val source = IrProgramPrinter().printToSingle(program)
        assertTrue("JavaOuter.JavaCallableNested()" in source, source)
        assertTrue("JavaCallableNestedReceiver.callable(Any())" in source, source)
        assertTrue("KotlinOuter.KotlinCallableNested()" in source, source)
        assertTrue("KotlinCallableNestedReceiver.callable(Any())" in source, source)
        val result = CrossLangFuzzerKotlinRunner.K2Jdk17Compiler.testProgram(source)
        assertTrue(result.success, "$result\\n$source")
    }

    private fun allClasses(program: IrProgram): List<IrClassDeclaration> {
        val result = mutableListOf<IrClassDeclaration>()
        fun visit(clazz: IrClassDeclaration) {
            result += clazz
            clazz.classes.forEach(::visit)
        }
        program.classes.forEach(::visit)
        return result
    }

    private fun function(name: String, language: Language, visibility: Visibility) =
        buildFunctionDeclaration {
            this.name = name
            this.language = language
            this.visibility = visibility
            isFinal = true
            body = buildBlock()
            parameterList = buildParameterList {
                parameters += buildParameter {
                    this.name = "value"
                    type = com.github.xyzboom.codesmith.ir.types.builtin.IrAny
                }
            }
        }
}
