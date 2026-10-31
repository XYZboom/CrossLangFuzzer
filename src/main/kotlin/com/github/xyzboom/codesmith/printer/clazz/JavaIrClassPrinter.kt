package com.github.xyzboom.codesmith.printer.clazz

import com.github.xyzboom.codesmith.ir.NullabilityAnnotationProvider
import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.Visibility
import com.github.xyzboom.codesmith.ir.Language.*
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.IrParameterList
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.functions
import com.github.xyzboom.codesmith.ir.properties
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.types.*
import com.github.xyzboom.codesmith.ir.ClassKind.*
import com.github.xyzboom.codesmith.ir.declarations.traverseOverride
import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType
import com.github.xyzboom.codesmith.ir.types.builtin.IrNothing
import com.github.xyzboom.codesmith.ir.types.builtin.IrUnit
import com.github.xyzboom.codesmith.printer.TypeContext
import com.github.xyzboom.codesmith.printer.TypeContext.*
import com.github.xyzboom.codesmith.printer.clazz.DefaultValueSupport.classDeclarationOrNull
import io.github.oshai.kotlinlogging.KotlinLogging

class JavaIrClassPrinter(
    private val majorLanguage: Language = KOTLIN,
    printStub: Boolean = true,
    private val nullabilityAnnotationProvider: NullabilityAnnotationProvider = NullabilityAnnotationProvider.JETBRAINS,
    /**
     * When `true`, a generated function body returns a real value instead of throwing, so that
     * the call site in `box()` really executes and the returned type takes part in the
     * compiler's type checking.
     */
    private val emitExecutableDefaults: Boolean = false,
) : AbstractIrClassPrinter(printStub = printStub) {
    private val builtInNamesInTypeArgument = buildMap {
        put(IrAny, "Object")
        put(IrNothing, "Void")
        when (majorLanguage) {
            KOTLIN, GROOVY4, GROOVY5 -> put(IrUnit, "Void")
            SCALA -> put(IrUnit, "BoxedUnit")
            JAVA -> throw IllegalStateException("Could not set major language to Java!")
        }
        Unit
    }

    private val builtInTypeInParameter = buildMap {
        put(IrAny, "Object")
        put(IrNothing, "Void")
        when (majorLanguage) {
            KOTLIN, GROOVY4, GROOVY5 -> put(IrUnit, "Void")
            SCALA -> put(IrUnit, "BoxedUnit")
            JAVA -> throw IllegalStateException("Could not set major language to Java!")
        }
        Unit
    }

    private val nullabilityImports = when (nullabilityAnnotationProvider) {
        NullabilityAnnotationProvider.JETBRAINS -> JETBRAINS_NULLABILITY_ANNOTATION_IMPORTS
        NullabilityAnnotationProvider.JSPECIFY -> JSPECIFY_NULLABILITY_ANNOTATION_IMPORTS
    }

    private val imports = nullabilityImports +
            when (majorLanguage) {
                KOTLIN, GROOVY4, GROOVY5 -> ""
                SCALA -> SCALA_RUNTIME_IMPORTS
                JAVA -> throw IllegalStateException("Could not set major language to Java!")
            }

    companion object {
        private val logger = KotlinLogging.logger {}
        private val builtInNames = buildMap {
            put(IrAny, "Object")
            put(IrNothing, "Void")
            put(IrUnit, "void")
        }
        const val JETBRAINS_NULLABILITY_ANNOTATION_IMPORTS =
            "import org.jetbrains.annotations.NotNull;\n" +
                    "import org.jetbrains.annotations.Nullable;\n"
        const val JSPECIFY_NULLABILITY_ANNOTATION_IMPORTS =
            "import org.jspecify.annotations.NonNull;\n" +
                    "import org.jspecify.annotations.Nullable;\n"
        const val NULLABILITY_ANNOTATION_IMPORTS = JETBRAINS_NULLABILITY_ANNOTATION_IMPORTS
        const val SCALA_RUNTIME_IMPORTS =
            "import scala.runtime.BoxedUnit;\n"
        const val TOP_LEVEL_CONTAINER_CLASS_NAME = "JavaTopLevelContainer"
        const val FUNCTION_BODY_TODO = "throw new UnsupportedOperationException();"
    }

    /**
     * value: The binary value of 1 corresponding to the position from the low position indicates that
     * the parameter of the index is a type parameter
     * Example: 0b1011 for function: f(a, b, c, d) means a, b and d is type parameter
     *          (in current or parent class)
     */
    private val functionParameterTypeHasTypeParameter = mutableMapOf<IrFunctionDeclaration, Int>()

    /**
     * Whether the body of the class that is currently printed throws
     * [DefaultValueSupport.UNAVAILABLE_EXCEPTION].
     *
     * The exception class is only emitted when it is really used, so that the generated Java
     * does not contain an unused class.
     */
    override fun print(element: IrClassDeclaration): String {
        val data = StringBuilder(imports)
        visitClassDeclaration(element, data)
        return data.toString()
    }

    override fun printTopLevelFunctionsAndProperties(program: IrProgram): String {
        return printTopLevelFunctionsAndProperties(program, TOP_LEVEL_CONTAINER_CLASS_NAME)
    }

    fun printTopLevelFunctionsAndProperties(program: IrProgram, containerClassName: String): String {
        val data = StringBuilder(imports)
        data.append("public final class $containerClassName {\n")
        indentCount++
        elementStack.push(program)
        for (function in program.functions.filter { it.language == JAVA }) {
            visitFunctionDeclaration(function, data)
        }
        for (property in program.properties.filter { it.language == JAVA }) {
            visitPropertyDeclaration(property, data)
        }
        indentCount--
        data.append("}")
        require(elementStack.pop() === program)
        return data.toString()
    }

    override fun printIrClassType(irClassType: ClassKind): String {
        return when (irClassType) {
            ABSTRACT -> "abstract class "
            INTERFACE -> "interface "
            OPEN -> "class "
            FINAL -> "final class "
        }
    }

    override fun printTypeDirectly(
        irType: IrType,
        typeContext: TypeContext,
        noNullabilityAnnotation: Boolean
    ): String {
        val chooseTypeMap = when (typeContext) {
            TypeArgument, TypeArgumentInReturnType -> builtInNamesInTypeArgument
            Parameter -> builtInTypeInParameter
            TypeParameterDeclaration, Other -> builtInNames
            else -> builtInNames
        }
        val typeStr = when (irType) {
            is IrNullableType -> {
                /**
                 * Since we are going to print [IrNullableType.innerType], so we close all nullability annotation
                 */
                val result = printType(
                    irType.innerType,
                    typeContext = typeContext,
                    noNullabilityAnnotation = true,
                )
                result
            }

            is IrPlatformType -> {
                /**
                 * Since we are going to print [IrPlatformType.innerType], so we close all nullability annotation
                 */
                val result = printType(
                    /**
                     * ```kt
                     * class A<T: Any?> {
                     *     fun foo(t: T!)
                     * }
                     * ```
                     * `A<Any>::foo::t` is `Any`, not `Any!`
                     * `A<Any?>::foo::t` is `Any?!` and is `Any!` in Java but is `Any?` in Kotlin.
                     */
                    irType.innerType.notNullType,
                    typeContext = typeContext,
                    noNullabilityAnnotation = true,
                )
                result
            }

            is IrBuiltInType -> chooseTypeMap[irType]
                ?: throw IllegalStateException("No such built-in type: $irType")

            is IrSimpleClassifier -> sourceClassName(irType.classDecl)
            is IrParameterizedClassifier -> {
                val sb = StringBuilder(sourceClassName(irType.classDecl))
                sb.append("<")
                // print type arg in the order of superType
                val typeArgs = irType.getTypeArguments()
                for ((index, typeParam) in irType.classDecl.typeParameters.withIndex()) {
                    val typeArg = typeArgs[IrTypeParameterName(typeParam.name)]!!.second
                    val rendered = when (typeArg.variance) {
                        IrVariance.INVARIANT -> printType(typeArg.type ?: IrAny, typeContext = TypeArgument)
                        IrVariance.COVARIANT -> "? extends ${printType(typeArg.type ?: IrAny, typeContext = TypeArgument)}"
                        IrVariance.CONTRAVARIANT -> "? super ${printType(typeArg.type ?: IrAny, typeContext = TypeArgument)}"
                        IrVariance.STAR -> "?"
                    }
                    sb.append(rendered)
                    if (index != irType.classDecl.typeParameters.lastIndex) {
                        sb.append(", ")
                    }
                }
                sb.append(">")
                sb.toString()
            }

            is IrTypeParameter -> irType.name

            is IrDefinitelyNotNullType -> printType(irType.innerType, noNullabilityAnnotation = true)

            else -> throw NoWhenBranchMatchedException()
        }
        val annotationStr = if (irType is IrNullableType) {
            "@Nullable"
        } else {
            when (nullabilityAnnotationProvider) {
                NullabilityAnnotationProvider.JETBRAINS -> "@NotNull"
                NullabilityAnnotationProvider.JSPECIFY -> "@NonNull"
            }
        }
        val finalAnnotationStr =
            if (noNullabilityAnnotation || typeStr == "void" ||
                (irType is IrTypeParameter && irType.deepUpperboundNullable()) ||
                irType is IrPlatformType
            ) {
                ""
            } else "$annotationStr "
        return "$finalAnnotationStr$typeStr"
    }

    override fun IrClassDeclaration.printExtendList(superType: IrType?, implList: List<IrType>): String {
        val sb = if (superType != null || implList.isNotEmpty()) {
            StringBuilder(" ")
        } else {
            StringBuilder()
        }
        if (superType != null) {
            sb.append("extends ")
            sb.append(printType(superType, noNullabilityAnnotation = true))
            sb.append(" ")
        }
        if (implList.isNotEmpty()) {
            if (classKind != INTERFACE) {
                sb.append("implements ")
            } else {
                sb.append("extends ")
            }
            for ((index, type) in implList.withIndex()) {
                sb.append(printType(type, noNullabilityAnnotation = true))
                if (index != implList.lastIndex) {
                    sb.append(", ")
                }
            }
        }
        return sb.toString()
    }

    override fun visitClassDeclaration(clazz: IrClassDeclaration, data: StringBuilder) {
        val topLevel = elementStack.none { it is IrClassDeclaration }
        val body = StringBuilder()
        super.visitClassDeclaration(clazz, body)

        data.append(indent)
        data.append(javaVisibility(clazz.visibility, topLevel))
        if (!topLevel) data.append("static ")
        data.append(printIrClassType(clazz.classKind))
        data.append(clazz.name)
        val typeParameters = clazz.typeParameters
        if (typeParameters.isNotEmpty()) {
            data.append("<")
            for ((index, typeParameter) in typeParameters.withIndex()) {
                data.append(
                    printType(
                        typeParameter, typeContext = TypeParameterDeclaration,
                        noNullabilityAnnotation = true
                    )
                )
                data.append(" extends ")
                data.append(
                    printType(typeParameter.upperbound)
                )
                if (index != typeParameters.lastIndex) {
                    data.append(", ")
                }
            }
            data.append(">")
        }
        data.append(clazz.printExtendList(clazz.superType, clazz.implementedTypes))
        data.append(" {\n")
        data.append(body)
        data.append(indent)
        data.append("}\n")
    }


    private fun javaVisibility(visibility: Visibility, topLevel: Boolean): String = when (visibility) {
        Visibility.PUBLIC -> "public "
        Visibility.PROTECTED -> if (topLevel) error("Top-level Java declarations cannot be protected") else "protected "
        Visibility.PACKAGE_PRIVATE -> ""
        Visibility.PRIVATE -> if (topLevel) error("Top-level Java declarations cannot be private") else "private "
        Visibility.INTERNAL -> error("Java declarations cannot be internal")
    }

    override fun visitFunctionDeclaration(function: IrFunctionDeclaration, data: StringBuilder) {
        if (function.isOverrideStub && !printStub) {
            return
        }
        val functionContainer: IrElement = elementStack.peek()
        elementStack.push(function)
        /**
         * Some version of Java's lexical analyzer is not greedy for matching multi line comments,
         * so multi line comments in stubs need to be disabled.
         */
        var printNullableAnnotation = function.isOverrideStub
        if (function.isOverrideStub) {
            data.append(indent)
            data.append("// stub\n")
            data.append(indent)
            data.append("/*\n")
        }
        if (function.isOverride) {
            data.append(indent)
            data.append("@Override\n")
        }
        data.append(indent)
        data.append(javaVisibility(function.visibility, topLevel = function.topLevel))
        if (function.body == null) {
            data.append("abstract ")
        } else {
            if (functionContainer is IrClassDeclaration && functionContainer.classKind == INTERFACE) {
                data.append("default ")
            }
        }
        if (function.topLevel) {
            data.append("static ")
        } else if (function.isFinal) {
            data.append("final ")
        }
        if (function.typeParameters.isNotEmpty()) {
            data.append("<")
            for ((index, typeParam) in function.typeParameters.withIndex()) {
                data.append(typeParam.name)
                data.append(" extends ")
                data.append(
                    printType(
                        typeParam.upperbound,
                        FunctionTypeParameterUpperBound
                    )
                )
                if (index != function.typeParameters.lastIndex) {
                    data.append(", ")
                }
            }
            data.append(">")
        }
        val myReturnTypeIsTypeParameter = function.run {
            returnType is IrTypeParameter ||
                    (returnType is IrNullableType && (returnType as IrNullableType).innerType is IrTypeParameter)
        }
        // IrUnit in Java is void if it is not type argument. It's Void otherwise.
        var anyOverrideReturnTypeIsTypeParameter = false
        // see this.functionParameterTypeHasTypeParameter
        var anyOverrideParameterIsTypeParameter = 0
        function.traverseOverride {
            val returnType = it.returnType
            logger.trace { returnType.toString() }
            if (returnType is IrTypeParameter ||
                (returnType is IrNullableType && returnType.innerType is IrTypeParameter)
            ) {
                anyOverrideReturnTypeIsTypeParameter = true
            }
            var current = 1
            for (param in it.parameterList.parameters) {
                val paramType = param.type
                if (paramType is IrTypeParameter ||
                    (paramType is IrNullableType && paramType.innerType is IrTypeParameter)
                ) {
                    anyOverrideParameterIsTypeParameter = anyOverrideParameterIsTypeParameter or current
                }
                current = current shl 1 // current << 1 (shl is shift left in Kotlin)
            }
        }
        functionParameterTypeHasTypeParameter[function] = anyOverrideParameterIsTypeParameter
        printNullableAnnotation = printNullableAnnotation ||
                anyOverrideReturnTypeIsTypeParameter || myReturnTypeIsTypeParameter
        data.append(
            printType(
                function.returnType,
                /**
                 * ```java
                 * class A<T extends @NotNull Object> {
                 *     public /*@Nullable*/ T func() {}
                 *     //                   ^
                 *     // type parameter T here is not a platform type! Instead, it is a NotNull type.
                 * }
                 * ```
                 */
                typeContext = ReturnType,
            )
        )
        data.append(" ")
        data.append(function.name)
        data.append("(")
        visitParameterList(function.parameterList, data)
        data.append(")")
        val body = function.body
        if (body != null) {
            data.append(" {\n")
            indentCount++
            visitBlock(body, data)
            indentCount--
            data.append(indent)
            data.append("}")
        } else {
            data.append(";")
        }
        data.append("\n")
        if (function.isOverrideStub) {
            data.append(indent)
            data.append("*/\n")
        }
        require(elementStack.pop() === function)
    }

    override fun visitParameterList(parameterList: IrParameterList, data: StringBuilder) {
        val parameters = parameterList.parameters
        val func = elementStack.peek() as IrFunctionDeclaration
        // see this.functionParameterTypeHasTypeParameter
        var current = 1
        val anyOverrideParameterIsTypeParameter = functionParameterTypeHasTypeParameter[func]!!
        for ((index, parameter) in parameters.withIndex()) {
            val parameterType = parameter.type
            data.append(
                printType(
                    parameterType, Parameter,
                    forcePrintNullableAnnotationIfIsTypeParameter = func.override.isEmpty()
//                        (anyOverrideParameterIsTypeParameter and current) != 0
                )
            )
            data.append(" ")
            data.append(parameter.name)
            if (index != parameters.lastIndex) {
                data.append(", ")
            }
            current = current shl 1
        }
    }

    override fun visitBlock(block: IrBlock, data: StringBuilder) {
        val function = elementStack.peek() as IrFunctionDeclaration
        if (block.expressions.isEmpty()) {
            data.append(indent)
            if (emitExecutableDefaults) {
                data.append(executableReturnStatement(function))
                data.append("\n")
            } else {
                data.append("throw new UnsupportedOperationException();\n")
            }
        } else {
            require(function.returnType === IrUnit /*|| block.expressions.last() is IrReturnExpression*/)
        }
        for (expression in block.expressions) {
            data.append(indent)
            expression.accept(this, data)
            data.append(";\n")
        }
    }

    /**
     * The statement that returns a real value of the function's return type.
     *
     * Only a generic type has no value, and then the statement throws
     * [DefaultValueSupport.UNAVAILABLE_EXCEPTION], which the call site in `box()` catches.
     */
    private fun executableReturnStatement(function: IrFunctionDeclaration): String {
        val owner = lastClass
        val value = DefaultValueSupport.defaultValue(function.returnType, owner, function)
        return when (value) {
            is DefaultValue.Unavailable -> {
                    "throw new UnsupportedOperationException();"
            }
            is DefaultValue.Null -> "return null;"
            is DefaultValue.UnitValue -> "return;"
            is DefaultValue.AnyValue -> "return new Object();"
            is DefaultValue.Constructor -> {
                "return ${constructorCallExpression(value)};"
            }
            is DefaultValue.AnonymousSubclass -> {
                "return ${printAnonymousSubclass(value, indent)};"
            }
        }
    }

    /**
     * The expression that creates an instance of a concrete class.
     */
    private fun constructorCallExpression(value: DefaultValue.Constructor): String {
        if (value.typeArguments.isEmpty()) return "new ${value.classDecl.name}()"
        val typeArguments = value.typeArguments.joinToString(", ") {
            printType(it, typeContext = TypeContext.TypeArgument)
        }
        return "new ${value.classDecl.name}<$typeArguments>()"
    }

    /**
     * An anonymous subclass of an abstract class or an interface, overriding every abstract
     * member with its default value.
     *
     * Java always uses `new Type() { }`, also for an interface, and an abstract class always
     * calls its constructor.
     *
     * @param baseIndent the indentation of the line the expression starts on.
     * @param depth the current nesting depth of anonymous subclasses.
     */
    private fun printAnonymousSubclass(
        default: DefaultValue.AnonymousSubclass,
        baseIndent: String,
        depth: Int = 0,
    ): String {
        if (depth >= DefaultValueSupport.MAX_ANONYMOUS_SUBCLASS_DEPTH) {
            return "throw new UnsupportedOperationException()"
        }
        val superType = anonymousSuperType(default)
        if (default.members.isEmpty()) {
            return "new $superType() { }"
        }
        val body = default.members.joinToString("\n") { member ->
            printMemberOverride(member, default, depth + 1)
        }
        val indentedBody = body.lines().joinToString("\n") { "$baseIndent    $it" }
        return "new $superType() {\n$indentedBody\n$baseIndent}"
    }

    /**
     * The supertype of the anonymous subclass, with its type arguments.
     */
    private fun anonymousSuperType(default: DefaultValue.AnonymousSubclass): String {
        if (default.typeArguments.isEmpty()) return default.classDecl.name
        val typeArguments = default.typeArguments.joinToString(", ") {
            printType(it, typeContext = TypeContext.TypeArgument)
        }
        return "${default.classDecl.name}<$typeArguments>"
    }

    /**
     * Renders one member of an anonymous subclass, overriding it with its default value.
     *
     * The type parameter bounds must be repeated, because an override with different bounds does
     * not implement the abstract member.
     */
    private fun printMemberOverride(
        member: IrFunctionDeclaration,
        default: DefaultValue.AnonymousSubclass,
        depth: Int,
    ): String {
        val typeArgMap = buildMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>> {
            default.typeArguments.forEachIndexed { index, typeArg ->
                val typeParameter = default.classDecl.typeParameters[index]
                put(IrTypeParameterName(typeParameter.name), typeParameter to typeArg)
            }
        }
        val returnTypeResolved = getActualTypeFromArguments(member.returnType, typeArgMap, true)
        val paramTypesResolved = member.parameterList.parameters.map { p ->
            getActualTypeFromArguments(p.type, typeArgMap, true)
        }
        if (member.containingClassName == default.classDecl.name || member.containingClassName in supertypeNames(default)) {
            if (returnTypeResolved !is IrTypeParameter && paramTypesResolved.any { p -> p.containsTypeParameter() }) {
                return "throw new ${DefaultValueSupport.UNAVAILABLE_EXCEPTION}();"
            }
        }
        val typeParameters = if (member.typeParameters.isEmpty()) {
            ""
        } else {
            member.typeParameters.joinToString(prefix = "<", postfix = ">") { typeParameter ->
                "${typeParameter.name} extends ${printType(typeParameter.upperbound)}"
            }
        }
        val parameters = member.parameterList.parameters.joinToString(", ") { parameter ->
            "${printType(parameter.type, typeContext = TypeContext.Parameter)} ${parameter.name}"
        }
        val returnType = printType(returnTypeResolved, typeContext = TypeContext.ReturnType)
        val body = memberBody(member, default, depth)
        return "@Override\npublic $typeParameters$returnType ${member.name}($parameters) $body"
    }

    private fun supertypeNames(default: DefaultValue.AnonymousSubclass): Set<String> {
        val names = mutableSetOf<String>()
        default.classDecl.superType?.let {
            when (it) {
                is com.github.xyzboom.codesmith.ir.types.IrSimpleClassifier -> names.add(it.classDecl.name)
                is com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier -> names.add(it.classDecl.name)
                is com.github.xyzboom.codesmith.ir.types.IrNullableType -> it.innerType.classDeclarationOrNull()?.name?.also { names.add(it) }
                is com.github.xyzboom.codesmith.ir.types.IrPlatformType -> it.innerType.classDeclarationOrNull()?.name?.also { names.add(it) }
                is com.github.xyzboom.codesmith.ir.types.IrDefinitelyNotNullType -> it.innerType.classDeclarationOrNull()?.name?.also { names.add(it) }
            }
        }
        default.classDecl.implementedTypes.forEach {
            when (it) {
                is com.github.xyzboom.codesmith.ir.types.IrSimpleClassifier -> names.add(it.classDecl.name)
                is com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier -> names.add(it.classDecl.name)
                is com.github.xyzboom.codesmith.ir.types.IrNullableType -> it.innerType.classDeclarationOrNull()?.name?.also { names.add(it) }
                is com.github.xyzboom.codesmith.ir.types.IrPlatformType -> it.innerType.classDeclarationOrNull()?.name?.also { names.add(it) }
                is com.github.xyzboom.codesmith.ir.types.IrDefinitelyNotNullType -> it.innerType.classDeclarationOrNull()?.name?.also { names.add(it) }
            }
        }
        return names
    }

    private fun IrType.classDeclarationOrNull(): IrClassDeclaration? = when (this) {
        is com.github.xyzboom.codesmith.ir.types.IrSimpleClassifier -> classDecl
        is com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier -> classDecl
        is com.github.xyzboom.codesmith.ir.types.IrNullableType -> innerType.classDeclarationOrNull()
        is com.github.xyzboom.codesmith.ir.types.IrPlatformType -> innerType.classDeclarationOrNull()
        is com.github.xyzboom.codesmith.ir.types.IrDefinitelyNotNullType -> innerType.classDeclarationOrNull()
        else -> null
    }

    /**
     * The body of an overridden member.
     *
     * `Void` has no non-null value in Java, so a member that returns `Void` throws instead of
     * returning `null`.
     */
    private fun memberBody(
        member: IrFunctionDeclaration,
        default: DefaultValue.AnonymousSubclass,
        depth: Int,
    ): String {
        val returnType = printType(member.returnType, typeContext = TypeContext.ReturnType)
        if (returnType == "Void") {
            return "{ throw new ${DefaultValueSupport.UNAVAILABLE_EXCEPTION}(); }"
        }
        val value = DefaultValueSupport.defaultValue(member.returnType, default.classDecl, member)
        return when (value) {
            is DefaultValue.Unavailable -> {
                    "{ throw new ${DefaultValueSupport.UNAVAILABLE_EXCEPTION}(); }"
            }
            is DefaultValue.UnitValue -> "{ }"
            else -> {
                // `Unavailable` means the return type has no constructible value; the
                // overridden member must still type-check, so it throws. A `throw` expression
                // cannot follow `return` in Java (`return throw ...` is a syntax error), so a
                // throwing value is emitted as a bare statement.
                val expression = executableValueExpression(value, default, depth)
                if (expression.startsWith("throw ")) {
                    "{ $expression; }"
                } else {
                    "{ return $expression; }"
                }
            }
        }
    }

    /**
     * The expression that produces a value of the given kind, used where a value is needed
     * instead of a statement, for example as the body of an overridden member.
     */
    private fun executableValueExpression(
        value: DefaultValue,
        default: DefaultValue.AnonymousSubclass,
        depth: Int,
    ): String = when (value) {
        is DefaultValue.Unavailable -> {
            "throw new UnsupportedOperationException()"
        }
        is DefaultValue.Null -> "null"
        is DefaultValue.UnitValue -> ""
        is DefaultValue.AnyValue -> "new Object()"
        is DefaultValue.Constructor -> constructorCallExpression(value)
        is DefaultValue.AnonymousSubclass -> printAnonymousSubclass(value, indent, depth + 1)
    }

    /*override fun visitNewExpression(newExpression: IrNew, data: StringBuilder) {
        data.append("new ")
        data.append(
            printType(
                newExpression.createType,
                printNullableAnnotation = false,
                noNullabilityAnnotation = true,
            )
        )
        data.append("()")
    }

    override fun visitFunctionCallExpression(functionCall: IrFunctionCall, data: StringBuilder) {
        val receiver = functionCall.receiver
        val target = functionCall.target
        if (receiver != null) {
            receiver.accept(this, data)
            data.append(".")
        } else if (target.topLevel) {
            if (target.language == KOTLIN) {
                data.append(KtIrClassPrinter.TOP_LEVEL_CONTAINER_CLASS_NAME)
            } else {
                data.append(TOP_LEVEL_CONTAINER_CLASS_NAME)
            }
            data.append(".")
        }
        data.append(target.name)
        data.append("(")
        for ((index, argument) in functionCall.arguments.withIndex()) {
            argument.accept(this, data)
            if (index != functionCall.arguments.lastIndex) {
                data.append(", ")
            }
        }
        data.append(")")
    }

    override fun visitReturnExpression(returnExpression: IrReturnExpression, data: StringBuilder) {
        data.append("return ")
        returnExpression.innerExpression?.accept(this, data)
    }

    override fun visitDefaultImplExpression(defaultImpl: IrDefaultImpl, data: StringBuilder) {
        data.append("null")
    }*/
}