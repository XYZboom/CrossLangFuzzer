package com.github.xyzboom.codesmith.printer

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.builder.buildModule
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildParameter
import com.github.xyzboom.codesmith.ir.expressions.builder.buildBlock
import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.classes
import com.github.xyzboom.codesmith.ir.functions
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildPlatformType
import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter
import com.github.xyzboom.codesmith.ir.types.type
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IrProgramPrinterBytecodeTest {
    @Test
    fun `printToSingle outputs module directives and handles single module`() {
        val classA = buildClassDeclaration {
            name = "ClassA"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
        }
        val program = buildProgram {
            modules += buildModule {
                name = "main"
                classes += classA
            }
        }
        val output = IrProgramPrinter().printToSingle(program)
        assertTrue(output.contains("// MODULE: annotations"))
        assertTrue(output.contains("// FILE: org/jetbrains/annotations/NotNull.java"))
        assertTrue(output.contains("// FILE: org/jetbrains/annotations/Nullable.java"))
        assertTrue(output.contains("// MODULE: main(annotations)"))
        assertTrue(output.contains("// MODULE: main_harness(annotations, main)"))
        assertTrue(output.indexOf("// MODULE: annotations") < output.indexOf("// MODULE: main(annotations)"))
        assertTrue(output.contains("// FILE: ClassA.kt"))
        assertTrue(output.contains("// FILE: main.kt"))
    }

    @Test
    fun `printToSingle outputs multiple modules with dependencies and mixed languages`() {
        val classJava = buildClassDeclaration {
            name = "JavaClass"
            language = Language.JAVA
            classKind = ClassKind.FINAL
        }
        val classKt = buildClassDeclaration {
            name = "KtClass"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
        }
        val m0 = buildModule {
            name = "m0"
            classes += classJava
        }
        val main = buildModule {
            name = "main"
            dependencies += m0
            classes += classKt
        }
        val program = buildProgram {
            modules += m0
            modules += main
        }
        val output = IrProgramPrinter().printToSingle(program)
        assertTrue(output.contains("// MODULE: annotations"))
        assertTrue(output.contains("// MODULE: m0(annotations)"))
        assertTrue(output.contains("// FILE: JavaClass.java"))
        assertTrue(output.contains("// MODULE: main(annotations, m0)"))
        assertTrue(output.contains("// MODULE: main_harness(annotations, m0, main)"))
        assertTrue(output.contains("// FILE: KtClass.kt"))
        assertTrue(output.indexOf("// MODULE: annotations") < output.indexOf("// MODULE: m0(annotations)"))
    }

    @Test
    fun `box calls constructible Kotlin and Java declarations once`() {
        val typeParameter = buildTypeParameter {
            name = "T"
            upperbound = IrAny
        }
        val kotlinClass = buildClassDeclaration {
            name = "KotlinOwner"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            functions += function("member", Language.KOTLIN)
            functions += function("overloaded", Language.KOTLIN)
            functions += function("overloaded", Language.KOTLIN, IrAny)
            functions += function("stub", Language.KOTLIN).apply { isOverrideStub = true }
        }
        val javaClass = buildClassDeclaration {
            name = "JavaOwner"
            language = Language.JAVA
            classKind = ClassKind.OPEN
            functions += function("member", Language.JAVA)
        }
        val genericClass = buildClassDeclaration {
            name = "GenericOwner"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            typeParameters += typeParameter
            functions += function("genericMember", Language.KOTLIN).apply {
                typeParameters += buildTypeParameter {
                    name = "U"
                    upperbound = IrAny
                }
            }
        }
        kotlinClass.functions += function(
            "arguments",
            Language.KOTLIN,
            buildNullableType { innerType = IrAny },
            com.github.xyzboom.codesmith.ir.types.builtin.IrUnit,
        )
        kotlinClass.functions += function(
            "platformArgument",
            Language.KOTLIN,
            buildPlatformType { innerType = IrAny },
        )
        kotlinClass.functions += function("parameterizedArgument", Language.KOTLIN, genericClass.type)
        val program = buildProgram {
            classes += kotlinClass
            classes += javaClass
            classes += genericClass
            functions += function("kotlinTop", Language.KOTLIN)
            functions += function("javaTop", Language.JAVA)
        }

        val output = IrProgramPrinter().printToSingle(program)

        assertEquals(1, output.windowed("KotlinOwnerReceiver.member()".length)
            .count { it == "KotlinOwnerReceiver.member()" })
        assertEquals(2, output.windowed("KotlinOwnerReceiver.overloaded".length)
            .count { it == "KotlinOwnerReceiver.overloaded" })
        assertTrue(output.contains("KotlinOwnerReceiver.overloaded(Any())"))
        assertTrue(output.contains("JavaOwnerReceiver.member()"))
        assertTrue(output.contains("GenericOwnerReceiver.genericMember<Any>()"))
        assertTrue(output.contains("KotlinOwnerReceiver.arguments((null as Any?), Unit)"))
        // Platform and parameterized arguments are constructible: platform IrAny uses its inner
        // concrete value, and the parameterized classifier uses its upper-bound arguments.
        assertTrue(output.contains("KotlinOwnerReceiver.platformArgument(Any())"))
        assertTrue(output.contains("KotlinOwnerReceiver.parameterizedArgument(GenericOwner<Any>())"))
        assertTrue(output.contains("kotlinTop()"))
        assertTrue(output.contains("JavaTopLevelContainer.javaTop()"))
        assertFalse(output.contains("KotlinOwnerReceiver.stub()"))
        assertEquals(10, output.windowed("catch (_: UnsupportedOperationException)".length)
            .count { it == "catch (_: UnsupportedOperationException)" })
        assertTrue(output.contains("throw UnsupportedOperationException()"))
        assertTrue(output.contains("throw new UnsupportedOperationException();"))
        assertFalse(output.contains("throw RuntimeException()"))
        assertFalse(output.contains("throw new RuntimeException();"))
        assertFalse(output.contains("catch (_: RuntimeException)"))
        assertFalse(output.contains("catch (_: Exception)"))
        assertFalse(output.contains("catch (_: Throwable)"))
        assertFalse(output.contains("catch (_: Error)"))
    }

    @Test
    fun `box skips abstract interface and type parameter values`() {
        val abstractOwner = buildClassDeclaration {
            name = "AbstractOwner"
            language = Language.KOTLIN
            classKind = ClassKind.ABSTRACT
            functions += function("abstractMember", Language.KOTLIN)
        }
        val interfaceOwner = buildClassDeclaration {
            name = "InterfaceOwner"
            language = Language.JAVA
            classKind = ClassKind.INTERFACE
            functions += function("interfaceMember", Language.JAVA)
        }
        val parameterType = buildTypeParameter {
            name = "T"
            upperbound = IrAny
        }
        val concreteOwner = buildClassDeclaration {
            name = "ConcreteOwner"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            functions += function("unconstructible", Language.KOTLIN, parameterType).apply {
                typeParameters += parameterType
            }
            functions += function(
                "dnnUnconstructible",
                Language.KOTLIN,
                com.github.xyzboom.codesmith.ir.types.builder.buildDefinitelyNotNullType { innerType = parameterType },
            ).apply { typeParameters += parameterType }
            functions += function(
                "nullableParameter",
                Language.KOTLIN,
                com.github.xyzboom.codesmith.ir.types.builder.buildNullableType { innerType = parameterType },
            ).apply { typeParameters += parameterType }
        }
        val program = buildProgram {
            classes += abstractOwner
            classes += interfaceOwner
            classes += concreteOwner
        }

        val output = IrProgramPrinter().printToSingle(program)

        assertFalse(output.contains("AbstractOwnerReceiver"))
        assertFalse(output.contains("InterfaceOwnerReceiver"))
        // Function-level type parameters are instantiated with their upper bounds at the call
        // site, so all three functions are called with constructible arguments.
        assertTrue(output.contains("ConcreteOwnerReceiver.unconstructible<Any>(Any())"))
        assertTrue(output.contains("ConcreteOwnerReceiver.dnnUnconstructible<Any>(Any())"))
        assertTrue(output.contains("ConcreteOwnerReceiver.nullableParameter<Any>((null as Any?))"))
        assertFalse(output.contains("TODO"))
        assertFalse(output.contains("null!!"))
    }

    @Test
    fun `nullable arguments use null and concrete upper bound type arguments`() {
        val ownerTypeParameter = buildTypeParameter {
            name = "T"
            upperbound = buildNullableType { innerType = IrAny }
        }
        val functionTypeParameter = buildTypeParameter {
            name = "U"
            upperbound = buildNullableType { innerType = IrAny }
        }
        val owner = buildClassDeclaration {
            name = "GenericNullableOwner"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
            typeParameters += ownerTypeParameter
            functions += function(
                "ownerNullable",
                Language.KOTLIN,
                buildNullableType { innerType = ownerTypeParameter },
            )
            functions += function(
                "functionNullable",
                Language.KOTLIN,
                buildNullableType { innerType = functionTypeParameter },
            ).apply { typeParameters += functionTypeParameter }
        }
        val program = buildProgram { classes += owner }

        val output = IrProgramPrinter().printToSingle(program)

        // The owner's T and the function's U are both instantiated with the non-null inner type
        // of their nullable upper bound (Any), so both nullable parameters receive a typed null.
        assertTrue(output.contains("val GenericNullableOwnerReceiver = GenericNullableOwner<Any>()"))
        assertTrue(output.contains("GenericNullableOwnerReceiver.ownerNullable((null as Any?))"))
        assertTrue(output.contains("GenericNullableOwnerReceiver.functionNullable<Any>((null as Any?))"))
    }

    @Test
    fun `top level declarations stay in generated module and harness depends on every module`() {
        val m0 = buildModule {
            name = "m0"
            functions += function("kotlinTop", Language.KOTLIN)
        }
        val main = buildModule {
            name = "main"
            dependencies += m0
            classes += buildClassDeclaration {
                name = "Owner"
                language = Language.KOTLIN
                classKind = ClassKind.FINAL
                functions += function("member", Language.KOTLIN)
            }
        }
        val program = buildProgram {
            modules += m0
            modules += main
        }

        val output = IrProgramPrinter().printToSingle(program)
        val topLevelFile = output.indexOf("// FILE: KotlinTopLevel_m0_0.kt")
        val harnessModule = output.indexOf("// MODULE: main_harness(annotations, m0, main)")

        assertTrue(topLevelFile >= 0)
        assertTrue(topLevelFile < harnessModule)
        assertTrue(output.indexOf("// FILE: main.kt") > harnessModule)
    }

    @Test
    fun `box supplies Kotlin Unit and skips Java Void parameters`() {
        val program = buildProgram {
            functions += function("kotlinUnit", Language.KOTLIN, com.github.xyzboom.codesmith.ir.types.builtin.IrUnit)
            functions += function("javaVoid", Language.JAVA, com.github.xyzboom.codesmith.ir.types.builtin.IrUnit)
            classes += buildClassDeclaration {
                name = "JavaVoidOwner"
                language = Language.JAVA
                classKind = ClassKind.FINAL
                functions += function("memberVoid", Language.JAVA, com.github.xyzboom.codesmith.ir.types.builtin.IrUnit)
            }
        }

        val output = IrProgramPrinter().printToSingle(program)

        assertTrue(output.contains("kotlinUnit(Unit)"))
        assertFalse(output.contains("JavaTopLevelContainer.javaVoid("))
        assertFalse(output.contains("JavaVoidOwnerReceiver.memberVoid("))
        assertFalse(output.contains("null!!"))
    }

    @Test
    fun `each module gets unique top level Kotlin file and Java container`() {
        val first = buildModule {
            name = "a-b"
            functions += function("kotlinFirst", Language.KOTLIN)
            functions += function("javaFirst", Language.JAVA)
        }
        val second = buildModule {
            name = "a_b"
            dependencies += first
            functions += function("kotlinSecond", Language.KOTLIN)
            functions += function("javaSecond", Language.JAVA)
        }
        val program = buildProgram {
            modules += first
            modules += second
        }

        val output = IrProgramPrinter().printToSingle(program)

        assertTrue(output.contains("// FILE: KotlinTopLevel_a_b_0.kt"))
        assertTrue(output.contains("// FILE: KotlinTopLevel_a_b_1.kt"))
        assertTrue(output.contains("// FILE: JavaTopLevelContainer_a_b_0.java"))
        assertTrue(output.contains("public final class JavaTopLevelContainer_a_b_0"))
        assertTrue(output.contains("// FILE: JavaTopLevelContainer_a_b_1.java"))
        assertTrue(output.contains("public final class JavaTopLevelContainer_a_b_1"))
        assertTrue(output.contains("JavaTopLevelContainer_a_b_0.javaFirst()"))
        assertTrue(output.contains("JavaTopLevelContainer_a_b_1.javaSecond()"))
        assertEquals(2, output.windowed("kotlinFirst()".length).count { it == "kotlinFirst()" })
        assertEquals(2, output.windowed("kotlinSecond()".length).count { it == "kotlinSecond()" })
    }

    @Test
    fun `generated module names avoid annotations main and main harness conflicts`() {
        val program = buildProgram {
            modules += buildModule { name = "annotations" }
            modules += buildModule { name = "main" }
            modules += buildModule { name = "main_harness" }
        }

        val output = IrProgramPrinter().printToSingle(program)

        assertTrue(output.contains("// MODULE: annotations_1\n"))
        assertTrue(output.contains("// MODULE: annotations(annotations_1)"))
        assertTrue(output.contains("// MODULE: main(annotations_1)"))
        assertTrue(output.contains("// MODULE: main_harness(annotations_1)"))
        assertTrue(output.contains(
            "// MODULE: main_harness_1(annotations_1, annotations, main, main_harness)"
        ))
    }

    @Test
    fun `duplicate module names get unique printed names and dependencies use printed names`() {
        val first = buildModule { name = "duplicate" }
        val second = buildModule {
            name = "duplicate"
            dependencies += first
        }
        val program = buildProgram {
            modules += first
            modules += second
        }

        val output = IrProgramPrinter().printToSingle(program)

        assertTrue(output.contains("// MODULE: duplicate(annotations)"))
        assertTrue(output.contains("// MODULE: duplicate_1(annotations, duplicate)"))
        assertTrue(output.contains("// MODULE: main(annotations, duplicate, duplicate_1)"))
    }

    @Test
    fun `auxiliary files facades and containers avoid existing class names`() {
        val module = buildModule {
            name = "module"
            classes += buildClassDeclaration {
                name = "JavaTopLevelContainer"
                language = Language.JAVA
                classKind = ClassKind.FINAL
            }
            classes += buildClassDeclaration {
                name = "KotlinTopLevel"
                language = Language.KOTLIN
                classKind = ClassKind.FINAL
            }
            classes += buildClassDeclaration {
                name = "KotlinTopLevelKt"
                language = Language.KOTLIN
                classKind = ClassKind.FINAL
            }
            classes += buildClassDeclaration {
                name = "mainKt"
                language = Language.KOTLIN
                classKind = ClassKind.FINAL
            }
            functions += function("javaTop", Language.JAVA)
            functions += function("kotlinTop", Language.KOTLIN)
        }
        val program = buildProgram { modules += module }

        val files = IrProgramPrinter().print(program)
        val output = IrProgramPrinter().printToSingle(program)

        assertTrue("JavaTopLevelContainer_1.java" in files)
        assertTrue("KotlinTopLevel_1.kt" in files)
        assertTrue("main.kt" in files)
        assertTrue(output.contains("// FILE: JavaTopLevelContainer_1.java"))
        assertTrue(output.contains("JavaTopLevelContainer_1.javaTop()"))
        assertTrue(output.contains("// FILE: KotlinTopLevel_1.kt"))
        assertTrue(output.contains("// FILE: main.kt"))
    }

    @Test
    fun `printer rejects nested declarations with a different source language`() {
        val nested = buildClassDeclaration {
            name = "KotlinNested"
            language = Language.KOTLIN
            classKind = ClassKind.FINAL
        }
        val outer = buildClassDeclaration {
            name = "JavaOuter"
            language = Language.JAVA
            classKind = ClassKind.OPEN
            classes += nested
        }
        val program = buildProgram { classes += outer }

        val exception = assertThrows<IllegalArgumentException> {
            IrProgramPrinter().print(program)
        }
        assertTrue(exception.message.orEmpty().contains("Nested class KotlinNested must use JAVA"))
    }

    @Test
    fun `Kotlin class main and trivial harness use different files`() {
        val program = buildProgram {
            modules += buildModule {
                name = "generated"
                classes += buildClassDeclaration {
                    name = "main"
                    language = Language.KOTLIN
                    classKind = ClassKind.FINAL
                }
            }
        }

        val files = IrProgramPrinter().print(program)
        val output = IrProgramPrinter().printToSingle(program)

        assertTrue("main.kt" in files)
        assertTrue(files.getValue("main.kt").contains("class main"))
        assertTrue("main_1.kt" in files)
        assertTrue(files.getValue("main_1.kt").contains("fun box(): String"))
        assertTrue(output.contains("// FILE: main.kt"))
        assertTrue(output.contains("class main"))
        assertTrue(output.contains("// FILE: main_1.kt"))
        assertTrue(output.contains("fun box(): String"))
    }

    @Test
    fun `nested declarations are rendered recursively with language-specific visibility`() {
        val javaLeaf = buildClassDeclaration {
            name = "JavaLeaf"
            language = Language.JAVA
            visibility = com.github.xyzboom.codesmith.ir.Visibility.PRIVATE
            classKind = ClassKind.FINAL
            functions += function("leafMethod", Language.JAVA).apply {
                visibility = com.github.xyzboom.codesmith.ir.Visibility.PACKAGE_PRIVATE
            }
        }
        val javaNested = buildClassDeclaration {
            name = "JavaNested"
            language = Language.JAVA
            visibility = com.github.xyzboom.codesmith.ir.Visibility.PROTECTED
            classKind = ClassKind.OPEN
            classes += javaLeaf
        }
        val javaOuter = buildClassDeclaration {
            name = "JavaOuter"
            language = Language.JAVA
            visibility = com.github.xyzboom.codesmith.ir.Visibility.PUBLIC
            classKind = ClassKind.OPEN
            classes += javaNested
        }

        val kotlinLeaf = buildClassDeclaration {
            name = "KotlinLeaf"
            language = Language.KOTLIN
            visibility = com.github.xyzboom.codesmith.ir.Visibility.PRIVATE
            classKind = ClassKind.FINAL
            functions += function("leafMethod", Language.KOTLIN).apply {
                visibility = com.github.xyzboom.codesmith.ir.Visibility.PROTECTED
            }
        }
        val kotlinNested = buildClassDeclaration {
            name = "KotlinNested"
            language = Language.KOTLIN
            visibility = com.github.xyzboom.codesmith.ir.Visibility.INTERNAL
            classKind = ClassKind.OPEN
            classes += kotlinLeaf
        }
        val kotlinOuter = buildClassDeclaration {
            name = "KotlinOuter"
            language = Language.KOTLIN
            visibility = com.github.xyzboom.codesmith.ir.Visibility.PUBLIC
            classKind = ClassKind.OPEN
            classes += kotlinNested
        }
        val program = buildProgram {
            classes += javaOuter
            classes += kotlinOuter
        }

        val files = IrProgramPrinter().print(program)
        val javaSource = files.getValue("JavaOuter.java")
        assertTrue(javaSource.contains("public class JavaOuter"))
        assertTrue(javaSource.contains("    protected static class JavaNested"))
        assertTrue(javaSource.contains("        private static final class JavaLeaf"))
        assertTrue(javaSource.contains("            final void leafMethod()"), javaSource)

        val kotlinSource = files.getValue("KotlinOuter.kt")
        assertTrue(kotlinSource.contains("public open class KotlinOuter"))
        assertTrue(kotlinSource.contains("    internal open class KotlinNested"))
        assertTrue(kotlinSource.contains("        private class KotlinLeaf"))
        assertTrue(kotlinSource.contains("            protected fun leafMethod(): Unit"))

        val scalaLeaf = buildClassDeclaration {
            name = "ScalaLeaf"
            language = Language.SCALA
            classKind = ClassKind.FINAL
        }
        val scalaOuter = buildClassDeclaration {
            name = "ScalaOuter"
            language = Language.SCALA
            classKind = ClassKind.OPEN
            classes += scalaLeaf
        }
        val scalaSource = com.github.xyzboom.codesmith.printer.clazz.ScalaIrClassPrinter().print(scalaOuter)
        assertTrue(scalaSource.contains("class ScalaOuter {"))
        assertTrue(scalaSource.contains("  final class ScalaLeaf"))
    }

    @Test
    fun `configured package is added to generated sources while default output stays unchanged`() {
        val program = buildProgram {
            classes += buildClassDeclaration {
                name = "JavaInFoo"
                language = Language.JAVA
                classKind = ClassKind.FINAL
            }
            classes += buildClassDeclaration {
                name = "KotlinInFoo"
                language = Language.KOTLIN
                classKind = ClassKind.FINAL
            }
            classes += buildClassDeclaration {
                name = "ScalaInFoo"
                language = Language.SCALA
                classKind = ClassKind.FINAL
            }
        }

        val defaultFiles = IrProgramPrinter(Language.SCALA).print(program)
        val packagedPrinter = IrProgramPrinter(Language.SCALA, packageName = "foo")
        val packagedFiles = packagedPrinter.print(program)

        assertEquals(defaultFiles.keys, packagedFiles.keys)
        assertFalse(defaultFiles.getValue("JavaInFoo.java").startsWith("package "))
        assertFalse(defaultFiles.getValue("KotlinInFoo.kt").startsWith("package "))
        assertFalse(defaultFiles.getValue("ScalaInFoo.scala").startsWith("package "))
        assertTrue(packagedFiles.getValue("JavaInFoo.java").startsWith("package foo;\n\n"))
        assertTrue(packagedFiles.getValue("KotlinInFoo.kt").startsWith("package foo\n\n"))
        assertTrue(packagedFiles.getValue("ScalaInFoo.scala").startsWith("package foo\n\n"))
        assertTrue(packagedFiles.getValue("DefaultValueUnavailableException.java").startsWith("package foo;\n\n"))
        assertEquals(
            defaultFiles.getValue("org/jetbrains/annotations/NotNull.java"),
            packagedFiles.getValue("org/jetbrains/annotations/NotNull.java"),
        )

        val singleFile = packagedPrinter.printToSingle(program)
        assertTrue(singleFile.contains("// FILE: ScalaInFoo.scala\npackage foo\n\n"))
        assertTrue(singleFile.contains("// FILE: DefaultValueUnavailableException.java\npackage foo;\n\n"))
        assertTrue(singleFile.contains("// FILE: org/jetbrains/annotations/NotNull.java\npackage org.jetbrains.annotations;"))
        assertTrue(singleFile.contains("// FILE: main.kt\npackage foo\n\n"))
    }

    private fun function(name: String, language: Language, vararg parameterTypes: com.github.xyzboom.codesmith.ir.types.IrType) =
        buildFunctionDeclaration {
            this.name = name
            this.language = language
            isFinal = true
            body = buildBlock()
            parameterList = buildParameterList {
                parameterTypes.forEachIndexed { index, parameterType ->
                    parameters += buildParameter {
                        this.name = "p$index"
                        type = parameterType
                    }
                }
            }
        }
}
