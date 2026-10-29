package com.github.xyzboom.codesmith.printer

import com.github.xyzboom.codesmith.ir.IrModule
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.NullabilityAnnotationProvider
import com.github.xyzboom.codesmith.ir.builder.buildModule
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.classes
import com.github.xyzboom.codesmith.ir.functions
import com.github.xyzboom.codesmith.ir.properties
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.types.IrDefinitelyNotNullType
import com.github.xyzboom.codesmith.ir.types.IrNullableType
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrPlatformType
import com.github.xyzboom.codesmith.ir.types.IrSimpleClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.areEqualTypes
import com.github.xyzboom.codesmith.ir.types.getActualTypeFromArguments
import com.github.xyzboom.codesmith.ir.types.getConcreteTypeArguments
import com.github.xyzboom.codesmith.ir.types.IrTypeParameterName
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builtin.IrNothing
import com.github.xyzboom.codesmith.ir.types.builtin.IrUnit
import com.github.xyzboom.codesmith.printer.clazz.JavaIrClassPrinter
import com.github.xyzboom.codesmith.printer.clazz.DefaultValue
import com.github.xyzboom.codesmith.printer.clazz.DefaultValueSupport
import com.github.xyzboom.codesmith.printer.clazz.KtIrClassPrinter
import com.github.xyzboom.codesmith.printer.clazz.ScalaIrClassPrinter
import com.github.xyzboom.codesmith.utils.mkdirsIfNotExists
import java.io.File
import java.util.IdentityHashMap

/**
 * Printer for whole program.
 * Key of result is file name and value is file content.
 */
@Suppress("unused")
class IrProgramPrinter(
    majorLanguage: Language = Language.KOTLIN,
    private val printStub: Boolean = true,
    private val packageName: String? = null,
) : IrPrinter<IrProgram, Map<String, String>> {
    private val majorLanguage = majorLanguage
    private lateinit var javaClassPrinter: JavaIrClassPrinter
    private val ktClassPrinter = KtIrClassPrinter(printStub = printStub)
    private val scalaClassPrinter = ScalaIrClassPrinter(printStub = printStub)

    init {
        require(packageName == null || packageName.matches(PACKAGE_NAME_PATTERN)) {
            "Invalid source package name: $packageName"
        }
    }

    companion object {
        private val PACKAGE_NAME_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")
        private val jetbrainsExtraJavaFile = buildMap {
            put(
                "org/jetbrains/annotations/NotNull.java",
                "package org.jetbrains.annotations;\n" +
                        "\n" +
                        "import java.lang.annotation.*;\n" +
                        "\n" +
                        "// org.jetbrains.annotations used in the compiler is version 13, whose @NotNull does not support the TYPE_USE target (version 15 does).\n" +
                        "// We're using our own @org.jetbrains.annotations.NotNull for testing purposes.\n" +
                        "@Documented\n" +
                        "@Retention(RetentionPolicy.CLASS)\n" +
                        "@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})\n" +
                        "public @interface NotNull {\n" +
                        "}"
            )
            put(
                "org/jetbrains/annotations/Nullable.java",
                "package org.jetbrains.annotations;\n" +
                        "\n" +
                        "import java.lang.annotation.*;\n" +
                        "\n" +
                        "// org.jetbrains.annotations used in the compiler is version 13, whose @Nullable does not support the TYPE_USE target (version 15 does).\n" +
                        "// We're using our own @org.jetbrains.annotations.Nullable for testing purposes.\n" +
                        "@Documented\n" +
                        "@Retention(RetentionPolicy.CLASS)\n" +
                        "@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})\n" +
                        "public @interface Nullable {\n" +
                        "}"
            )
        }
        // CLF currently models nullability at each type-use position, so only the type-use
        // JSpecify annotations are emitted. Add NullMarked/NullUnmarked here if scope-level
        // default nullness becomes part of the IR and mutation model.
        private val jspecifyExtraJavaFile = buildMap {
            put(
                "org/jspecify/annotations/NonNull.java",
                "package org.jspecify.annotations;\n" +
                        "\n" +
                        "import java.lang.annotation.*;\n" +
                        "\n" +
                        "@Documented\n" +
                        "@Retention(RetentionPolicy.RUNTIME)\n" +
                        "@Target({ElementType.TYPE_USE})\n" +
                        "public @interface NonNull {\n" +
                        "}"
            )
            put(
                "org/jspecify/annotations/Nullable.java",
                "package org.jspecify.annotations;\n" +
                        "\n" +
                        "import java.lang.annotation.*;\n" +
                        "\n" +
                        "@Documented\n" +
                        "@Retention(RetentionPolicy.RUNTIME)\n" +
                        "@Target({ElementType.TYPE_USE})\n" +
                        "public @interface Nullable {\n" +
                        "}"
            )
        }
        // The harness throws this exception when a generic value cannot be constructed.
        // It must be on the classpath of every module (included in the annotations module).
        private val harnessExceptionJavaFile = mapOf(
            "${DefaultValueSupport.UNAVAILABLE_EXCEPTION}.java" to
                "public class ${DefaultValueSupport.UNAVAILABLE_EXCEPTION} extends UnsupportedOperationException {\n" +
                    "    public ${DefaultValueSupport.UNAVAILABLE_EXCEPTION}() { }\n" +
                    "}"
        )

        const val ANNOTATIONS_MODULE_NAME = "annotations"
        const val MAIN_KT_NAME = "main.kt"
        const val KOTLIN_TOP_LEVEL_KT_NAME = "KotlinTopLevel.kt"
        val extraSourceFileNames = listOf(
            MAIN_KT_NAME,
            KOTLIN_TOP_LEVEL_KT_NAME,
            *jetbrainsExtraJavaFile.keys.toTypedArray(),
            *jspecifyExtraJavaFile.keys.toTypedArray(),
            *harnessExceptionJavaFile.keys.toTypedArray(),
        )
    }

    private fun extraJavaFile(provider: NullabilityAnnotationProvider): Map<String, String> =
        when (provider) {
            NullabilityAnnotationProvider.JETBRAINS -> jetbrainsExtraJavaFile + harnessExceptionJavaFile
            NullabilityAnnotationProvider.JSPECIFY -> jspecifyExtraJavaFile + harnessExceptionJavaFile
        }

    override fun print(element: IrProgram): Map<String, String> {
        val result = mutableMapOf<String, String>()
        javaClassPrinter = JavaIrClassPrinter(
            majorLanguage,
            printStub = printStub,
            nullabilityAnnotationProvider = element.nullabilityAnnotationProvider,
        )
        val sourceClassNames = IdentityHashMap<IrClassDeclaration, String>()
        fun registerClassName(clazz: IrClassDeclaration, enclosingName: String?) {
            val fullName = if (enclosingName == null) clazz.name else "$enclosingName.${clazz.name}"
            sourceClassNames[clazz] = fullName
            clazz.classes.forEach { registerClassName(it, fullName) }
        }
        element.classes.forEach { registerClassName(it, null) }
        javaClassPrinter.setSourceClassNames(sourceClassNames)
        ktClassPrinter.setSourceClassNames(sourceClassNames)
        for (clazz in element.classes) {
            val (fileName, content) = when (clazz.language) {
                Language.KOTLIN -> "${clazz.name}.kt" to ktClassPrinter.print(clazz)
                Language.JAVA -> "${clazz.name}.java" to javaClassPrinter.print(clazz)
                Language.GROOVY4, Language.GROOVY5 -> "${clazz.name}.groovy" to javaClassPrinter.print(clazz)
                Language.SCALA -> "${clazz.name}.scala" to scalaClassPrinter.print(clazz)
            }
            result[fileName] = content
        }
        val auxiliaryNames = AuxiliaryNameAllocator(element.classes)
        val javaTopLevelContainers = IdentityHashMap<IrFunctionDeclaration, String>()
        if (element.classes.any { it.language == Language.JAVA } ||
            element.functions.any { it.language == Language.JAVA }
        ) {
            val containerName = auxiliaryNames.javaContainer(JavaIrClassPrinter.TOP_LEVEL_CONTAINER_CLASS_NAME)
            element.functions.filter { it.language == Language.JAVA }.forEach {
                javaTopLevelContainers[it] = containerName
            }
            result["$containerName.java"] =
                javaClassPrinter.printTopLevelFunctionsAndProperties(element, containerName)
        }
        result.putAll(extraJavaFile(element.nullabilityAnnotationProvider))
        if (majorLanguage == Language.KOTLIN) {
            val kotlinTopLevelFileName = auxiliaryNames.kotlinFile("KotlinTopLevel")
            result[kotlinTopLevelFileName] = ktClassPrinter.printTopLevelFunctionsAndProperties(element)
            result[auxiliaryNames.harnessFileName()] = printHarness(element, javaTopLevelContainers)
        }
        for ((fileName, content) in result.toMap()) {
            result[fileName] = addPackage(fileName, content)
        }
        return result
    }

    private fun addPackage(fileName: String, content: String): String {
        val sourcePackageName = packageName ?: return content
        if (fileName in jetbrainsExtraJavaFile.keys || fileName in jspecifyExtraJavaFile.keys) return content
        val separator = if (fileName.endsWith(".java")) ";\n\n" else "\n\n"
        return "package $sourcePackageName$separator$content"
    }

    fun saveTo(path: String, program: IrProgram) {
        val saveDir = File(path)
        if (!saveDir.exists()) {
            saveDir.mkdirs()
        }
        val fileMap = print(program)
        saveFileMap(fileMap, path)
    }

    fun saveFileMap(fileMap: Map<String, String>, path: String) {
        for ((fileName, content) in fileMap) {
            val file = File(path, fileName)
            file.parentFile.mkdirsIfNotExists()
            file.createNewFile()
            file.writeText(content)
        }
    }

    fun printToSingle(element: IrProgram): String {
        val map = print(element)
        val sb = StringBuilder(
            "// JVM_DEFAULT_MODE: no-compatibility\n" +
                    "// TARGET_BACKEND: JVM\n" +
                    "// JVM_TARGET: 1.8\n"
        )
        val effectiveModules: List<IrModule> = if (element.modules.isEmpty()) {
            listOf(buildModule {
                name = "main"
                classes.addAll(element.classes)
                functions.addAll(element.functions)
                properties.addAll(element.properties)
            })
        } else {
            element.modules
        }
        val printedModuleNames = IdentityHashMap<IrModule, String>()
        val usedModuleNames = mutableSetOf<String>()
        val printedModuleNamesSet = mutableSetOf<String>()
        for (module in effectiveModules) {
            val printedName = uniqueName(module.name, printedModuleNamesSet)
            printedModuleNames[module] = printedName
            printedModuleNamesSet += printedName
            usedModuleNames += printedName
        }
        val annotationsModuleName = uniqueName(ANNOTATIONS_MODULE_NAME, usedModuleNames)
        usedModuleNames += annotationsModuleName
        val preferredHarnessModuleName = if ("main" in usedModuleNames) "main_harness" else "main"
        val harnessModuleName = uniqueName(preferredHarnessModuleName, usedModuleNames)
        val javaTopLevelContainers = IdentityHashMap<IrFunctionDeclaration, String>()
        val auxiliaryNames = AuxiliaryNameAllocator(element.classes)

        sb.append("// MODULE: $annotationsModuleName\n")
        appendFiles(
            sb,
            extraJavaFile(element.nullabilityAnnotationProvider).mapValues { (fileName, content) ->
                addPackage(fileName, content)
            },
        )

        for ((moduleIndex, module) in effectiveModules.withIndex()) {
            val printedModuleName = printedModuleNames.getValue(module)
            val depNames = listOf(annotationsModuleName) + module.dependencies.mapNotNull { dependency ->
                printedModuleNames[dependency]
            }
            val depString = "(${depNames.joinToString(", ")})"
            sb.append("// MODULE: $printedModuleName$depString\n")

            val modFileNames = buildSet {
                for (clazz in module.classes) {
                    add("${clazz.name}.${clazz.language.extension}")
                }
            }
            appendFiles(sb, map.filterKeys { it in modFileNames })
            if (module.functions.any { it.language == Language.KOTLIN } ||
                module.properties.any { it.language == Language.KOTLIN }
            ) {
                val kotlinTopLevelFileName = auxiliaryNames.kotlinFile(kotlinTopLevelBaseName(
                    printedModuleName, moduleIndex, effectiveModules.size
                ))
                val moduleProgram = buildProgram {
                    modules += module
                    nullabilityAnnotationProvider = element.nullabilityAnnotationProvider
                }
                appendFiles(
                    sb,
                    mapOf(kotlinTopLevelFileName to addPackage(
                        kotlinTopLevelFileName,
                        ktClassPrinter.printTopLevelFunctionsAndProperties(moduleProgram),
                    )),
                )
            }
            if (module.functions.any { it.language == Language.JAVA } ||
                module.properties.any { it.language == Language.JAVA }
            ) {
                val containerName = auxiliaryNames.javaContainer(javaTopLevelContainerBaseName(
                    printedModuleName, moduleIndex, effectiveModules.size
                ))
                module.functions.filter { it.language == Language.JAVA }.forEach {
                    javaTopLevelContainers[it] = containerName
                }
                val moduleProgram = buildProgram {
                    modules += module
                    nullabilityAnnotationProvider = element.nullabilityAnnotationProvider
                }
                appendFiles(
                    sb,
                    mapOf("$containerName.java" to addPackage(
                        "$containerName.java",
                        javaClassPrinter.printTopLevelFunctionsAndProperties(moduleProgram, containerName),
                    )),
                )
            }
        }
        val harnessDependencies: List<String>
        if (hasHarnessEntryCollision(element)) {
            // The harness falls back to the trivial `fun box()` which calls nothing, so it does
            // not need to see the generated classes. Drop the modules that hold a `box`/`main`
            // declaration, because importing those makes `box()`/`main()` ambiguous with the
            // harness entry inside the harness module's own injected `main()`.
            harnessDependencies = listOf(annotationsModuleName) + effectiveModules
                .filter { module ->
                    module.classes.none { it.name == "box" || it.name == "main" } &&
                        module.functions.none { it.language == Language.KOTLIN && (it.name == "box" || it.name == "main") }
                }
                .map { printedModuleNames.getValue(it) }
        } else {
            harnessDependencies = listOf(annotationsModuleName) + effectiveModules.map { printedModuleNames.getValue(it) }
        }
        sb.append("// MODULE: $harnessModuleName(${harnessDependencies.joinToString(", ")})\n")
        appendFiles(
            sb,
            mapOf(auxiliaryNames.harnessFileName() to addPackage(
                auxiliaryNames.harnessFileName(),
                printHarness(element, javaTopLevelContainers),
            )),
        )
        return sb.toString()
    }

    /**
     * Whether any declaration in [program] uses the harness/framework entry-point names
     * `box` or `main`.
     *
     * The compiler test framework injects a `fun main()` into every module that declares a
     * `fun box()`, and that injected `main()` calls `box()` and compares it with a `String`.
     * When the generated program itself declares `box`/`main` as a top-level function or a
     * class, `box()`/`main()` become ambiguous between the harness and that declaration, and
     * the framework rejects the program. Such a program cannot be run through the executable
     * call harness at all; the printer falls back to the trivial `fun box(): String` that
     * returns `"OK"` so the program still compiles instead of being recorded as a
     * false-positive compiler bug.
     */
    private fun hasHarnessEntryCollision(program: IrProgram): Boolean =
        program.classes.any { it.name == "box" || it.name == "main" } ||
            program.functions.any { it.language == Language.KOTLIN && (it.name == "box" || it.name == "main") }

    /**
     * The harness of the generated program: `box()` calls every generated function through the
     * three receiver shapes, so that the call sites take part in the compiler's type checking
     * and really execute.
     */
    private fun printHarness(
        program: IrProgram,
        javaTopLevelContainers: Map<IrFunctionDeclaration, String> = emptyMap(),
    ): String {
        // See [hasHarnessEntryCollision]: a `box`/`main` declaration cannot coexist with the
        // executable call harness. Fall back to the trivial `fun box(): String { return "OK" }`
        // so the program still compiles instead of being recorded as a false-positive compiler
        // bug.
        if (hasHarnessEntryCollision(program)) {
            return buildString {
                append("fun box(): String {\n")
                append("    return \"OK\"\n")
                append("}\n")
            }
        }
        return buildString {
            append("fun box(): String {\n")
            for (function in program.functions) {
                if (function.language == Language.KOTLIN && (function.name == "box" || function.name == "main")) {
                    continue
                }
                appendCall(
                    function,
                    declaringType = null,
                    receiver = null,
                    javaTopLevelContainer = javaTopLevelContainers[function],
                )
            }
            val concreteReceiverNames = IdentityHashMap<IrClassDeclaration, String>()
            val usedReceiverNames = mutableSetOf<String>()
            for (receiver in CallMatrix.receivers(program.classes)) {
                appendReceiverCalls(receiver, concreteReceiverNames, usedReceiverNames)
            }
            append("    return \"OK\"\n")
            append("}\n")
        }
    }

    /** Emits one receiver shape after all of its calls have been proven renderable. */
    private fun StringBuilder.appendReceiverCalls(
        shape: MatrixReceiver,
        concreteReceiverNames: IdentityHashMap<IrClassDeclaration, String>,
        usedReceiverNames: MutableSet<String>,
    ) {
        val concreteType = CallMatrix.concreteType(shape.receiverClass)
        val isConcreteShape = areEqualTypes(shape.staticType, concreteType)
        val preferredName = if (isConcreteShape) {
            "${shape.receiverClass.name}Receiver"
        } else {
            val staticClassName = when (val type = shape.staticType) {
                is IrSimpleClassifier -> type.classDecl.name
                is IrParameterizedClassifier -> type.classDecl.name
                else -> return
            }
            "${shape.receiverClass.name}As${staticClassName}Receiver"
        }
        val receiverName = uniqueName(preferredName, usedReceiverNames)
        val calls = shape.calls.mapNotNull { call ->
            renderCall(
                function = call.function,
                declaringType = call.declaringType,
                receiver = receiverName,
                javaTopLevelContainer = null,
            )
        }
        if (calls.isEmpty()) return

        val concreteReceiverName = concreteReceiverNames[shape.receiverClass] ?: run {
            val allocated = if (isConcreteShape) receiverName else
                uniqueName("${shape.receiverClass.name}Receiver", usedReceiverNames)
            val expression = receiverExpression(shape.receiverClass) ?: return
            append("    val $allocated = $expression\n")
            usedReceiverNames += allocated
            concreteReceiverNames[shape.receiverClass] = allocated
            allocated
        }

        val actualReceiverName = if (isConcreteShape) {
            concreteReceiverName
        } else {
            append("    val $receiverName: ${ktClassPrinter.printType(shape.staticType)} = $concreteReceiverName\n")
            usedReceiverNames += receiverName
            receiverName
        }
        calls.forEach { append(it.replace("$receiverName.", "$actualReceiverName.")) }
    }

    /** The expression that creates a concrete no-arg receiver. */
    private fun receiverExpression(clazz: IrClassDeclaration): String? {
        if (clazz.name == "box" || clazz.name == "main") return null
        if (!CallMatrix.isInstantiable(clazz)) return null
        return "${ktClassPrinter.printType(CallMatrix.concreteType(clazz))}()"
    }

    private fun StringBuilder.appendCall(
        function: IrFunctionDeclaration,
        declaringType: IrType?,
        receiver: String?,
        javaTopLevelContainer: String?,
    ) {
        append(renderCall(function, declaringType, receiver, javaTopLevelContainer) ?: return)
    }

    private fun renderCall(
        function: IrFunctionDeclaration,
        declaringType: IrType?,
        receiver: String?,
        javaTopLevelContainer: String?,
    ): String? {
        if (function.isOverrideStub) return null
        val ownerArguments = (declaringType as? IrParameterizedClassifier)
            ?.getConcreteTypeArguments()
            .orEmpty()

        val recorded = mutableMapOf<IrTypeParameterName, Pair<IrTypeParameter, IrType>>()
        ownerArguments.forEach { (name, pair) -> recorded[name] = pair }

        val typeArgumentsString = if (function.typeParameters.isEmpty()) {
            ""
        } else {
            val resolvedArgs = function.typeParameters.map { typeParameter ->
                val bound = CallMatrix.resolveUpperBound(typeParameter, recorded)
                recorded[IrTypeParameterName(typeParameter.name)] = typeParameter to bound
                bound
            }
            if (resolvedArgs.any(::containsTypeParameter)) return null
            resolvedArgs.joinToString(prefix = "<", postfix = ">") { ktClassPrinter.printType(it) }
        }

        val parameterTypes = function.parameterList.parameters.map { parameter ->
            getActualTypeFromArguments(parameter.type, recorded, true)
        }

        // Bare or nested type parameters cannot be constructed faithfully by the simple harness.
        if (parameterTypes.any(::containsTypeParameter)) return null
        val arguments = parameterTypes.map { type ->
            argumentValue(type, function)
        }
        if (arguments.any { it == null }) return null

        val callTarget = when {
            receiver != null -> "$receiver.${function.name}$typeArgumentsString"
            function.language == Language.JAVA ->
                "${javaTopLevelContainer ?: JavaIrClassPrinter.TOP_LEVEL_CONTAINER_CLASS_NAME}.${function.name}$typeArgumentsString"
            else -> "${function.name}$typeArgumentsString"
        }
        return buildString {
            append("    try {\n")
            append("        $callTarget(${arguments.joinToString(", ")})\n")
            append("    } catch (_: UnsupportedOperationException) {\n")
            append("    }\n")
        }
    }

    /** Whether [irType] transitively contains any type parameter reference. */
    private fun containsTypeParameter(irType: IrType): Boolean = when (irType) {
        is com.github.xyzboom.codesmith.ir.types.IrTypeParameter -> true
        is com.github.xyzboom.codesmith.ir.types.IrNullableType -> containsTypeParameter(irType.innerType)
        is com.github.xyzboom.codesmith.ir.types.IrDefinitelyNotNullType -> containsTypeParameter(irType.innerType)
        is com.github.xyzboom.codesmith.ir.types.IrPlatformType -> containsTypeParameter(irType.innerType)
        is com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier ->
            irType.arguments.values.any { (_, argument) ->
                argument.type?.let(::containsTypeParameter) == true
            }
        else -> false
    }

    /**
     * The value passed as an argument of the given type.
     */
    private fun argumentValue(
        type: IrType,
        function: IrFunctionDeclaration,
    ): String? {
        // A Java parameter printed as `Void` has no non-null value, and `Unit` is not
        // assignable to java.lang.Void, so a nullable Unit/Nothing inside a Java signature
        // cannot be passed at all.
        if (function.language == Language.JAVA &&
            (type === IrUnit || type === IrNothing ||
                (type is IrNullableType && (type.innerType === IrUnit || type.innerType === IrNothing)))
        ) {
            return null
        }
        val value = DefaultValueSupport.defaultValue(type, null, function)
        return when (value) {
            is DefaultValue.Unavailable -> null
            is DefaultValue.Null -> "(null as ${ktClassPrinter.printType(type)})"
            is DefaultValue.UnitValue -> if (function.language == Language.JAVA) null else "Unit"
            is DefaultValue.AnyValue -> "Any()"
            is DefaultValue.Constructor -> {
                val args = value.typeArguments.joinToString(", ") { ktClassPrinter.printType(it) }
                if (args.isEmpty()) "${value.classDecl.name}()" else "${value.classDecl.name}<$args>()"
            }
            is DefaultValue.AnonymousSubclass -> ktClassPrinter.printAnonymousSubclass(value, "        ")
        }
    }

    private fun uniqueName(preferredName: String, usedNames: Set<String>): String {
        if (preferredName !in usedNames) return preferredName
        var suffix = 1
        while ("${preferredName}_$suffix" in usedNames) suffix++
        return "${preferredName}_$suffix"
    }

    private fun kotlinTopLevelBaseName(printedModuleName: String, moduleIndex: Int, moduleCount: Int): String =
        if (moduleCount == 1) "KotlinTopLevel"
        else "KotlinTopLevel_${sanitizedModuleName(printedModuleName)}_$moduleIndex"

    private fun javaTopLevelContainerBaseName(printedModuleName: String, moduleIndex: Int, moduleCount: Int): String =
        if (moduleCount == 1) JavaIrClassPrinter.TOP_LEVEL_CONTAINER_CLASS_NAME
        else "${JavaIrClassPrinter.TOP_LEVEL_CONTAINER_CLASS_NAME}_${sanitizedModuleName(printedModuleName)}_$moduleIndex"

    private class AuxiliaryNameAllocator(
        classes: List<IrClassDeclaration>,
    ) {
        private val usedJvmClassNames = classes.mapTo(mutableSetOf()) { it.name }
        private val usedFileNames = classes.mapTo(mutableSetOf()) { "${it.name}.${it.language.extension}" }
        private val usedHelperNames = mutableSetOf<String>()

        fun javaContainer(preferredName: String): String {
            var candidate = preferredName
            var suffix = 1
            while (candidate in usedJvmClassNames || "$candidate.java" in usedFileNames ||
                candidate in usedHelperNames
            ) {
                candidate = "${preferredName}_${suffix++}"
            }
            usedJvmClassNames += candidate
            usedFileNames += "$candidate.java"
            usedHelperNames += candidate
            return candidate
        }

        fun kotlinFile(preferredBaseName: String): String {
            var candidate = preferredBaseName
            var suffix = 1
            while ("$candidate.kt" in usedFileNames ||
                kotlinFacade(candidate) in usedJvmClassNames ||
                candidate in usedHelperNames
            ) {
                candidate = "${preferredBaseName}_${suffix++}"
            }
            usedFileNames += "$candidate.kt"
            usedJvmClassNames += kotlinFacade(candidate)
            usedHelperNames += candidate
            return "$candidate.kt"
        }

        fun harnessFileName(): String {
            var candidate = "main"
            var suffix = 1
            while ("$candidate.kt" in usedFileNames ||
                kotlinFacade(candidate) in usedJvmClassNames ||
                candidate in usedHelperNames
            ) {
                candidate = "main_${suffix++}"
            }
            usedFileNames += "$candidate.kt"
            usedJvmClassNames += kotlinFacade(candidate)
            usedHelperNames += candidate
            return "$candidate.kt"
        }

        private fun kotlinFacade(fileBaseName: String): String {
            val first = fileBaseName.first().uppercase()
            return "${first}${fileBaseName.substring(1)}Kt"
        }
    }

    private fun sanitizedModuleName(moduleName: String): String {
        val sanitized = buildString {
            for ((index, character) in moduleName.withIndex()) {
                when {
                    character == '_' || character.isLetterOrDigit() -> {
                        if (index == 0 && character.isDigit()) append('_')
                        append(character)
                    }
                    else -> append('_')
                }
            }
        }
        return sanitized.ifEmpty { "module" }
    }

    private fun appendFiles(sb: StringBuilder, files: Map<String, String>) {
        for ((key, value) in files) {
            sb.append("// FILE: $key\n")
            sb.append(value)
            sb.append("\n")
        }
    }
}
