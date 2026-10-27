package com.github.xyzboom.codesmith.printer.clazz

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.Visibility
import com.github.xyzboom.codesmith.ir.IrParameterList
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.functions
import com.github.xyzboom.codesmith.ir.properties
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.types.*
import com.github.xyzboom.codesmith.ir.ClassKind.*
import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType
import com.github.xyzboom.codesmith.ir.types.builtin.IrNothing
import com.github.xyzboom.codesmith.ir.types.builtin.IrUnit
import com.github.xyzboom.codesmith.printer.clazz.DefaultValueSupport.classDeclarationOrNull
import com.github.xyzboom.codesmith.printer.TypeContext
import com.github.xyzboom.codesmith.printer.TypeContext.TypeParameterDeclaration

class KtIrClassPrinter(
    printStub: Boolean = true,
    /** When `true`, a generated function body returns a real value instead of throwing, so that
     * the call site in `box()` really executes and the returned type takes part in the
     * compiler's type checking.
     */
    val emitExecutableDefaults: Boolean = false,
) : AbstractIrClassPrinter(printStub = printStub) {

    companion object {
        private val builtInNames = buildMap {
            put(IrAny, "Any")
            put(IrNothing, "Nothing")
            put(IrUnit, "Unit")
        }

        /** For java interaction */
        const val TOP_LEVEL_CONTAINER_CLASS_NAME = "MainKt"
    }

    override fun print(element: IrClassDeclaration): String {
        val data = StringBuilder()
        visitClassDeclaration(element, data)
        return data.toString()
    }

    override fun printTopLevelFunctionsAndProperties(program: IrProgram): String {
        val data = StringBuilder()
        elementStack.push(program)
        for (function in program.functions.filter { it.language == Language.KOTLIN }) {
            visitFunctionDeclaration(function, data)
        }
        for (property in program.properties.filter { it.language == Language.KOTLIN }) {
            visitPropertyDeclaration(property, data)
        }
        require(elementStack.pop() === program)
        return data.toString()
    }

    override fun printTypeDirectly(
        irType: IrType,
        typeContext: TypeContext,
        noNullabilityAnnotation: Boolean,
    ): String {
        return when (irType) {
            is IrNullableType -> return "${printType(irType.innerType)}?"
            is IrBuiltInType -> return builtInNames[irType]
                ?: throw IllegalStateException("No such built-in type: $irType")

            is IrClassifier ->
                when (irType) {
                    is IrSimpleClassifier -> sourceClassName(irType.classDecl)
                    is IrParameterizedClassifier -> {
                        val sb = StringBuilder(sourceClassName(irType.classDecl))
                        sb.append("<")
                        // print type arg in the order of superType
                        val typeArgs = irType.getTypeArguments()
                        for ((index, typeParam) in irType.classDecl.typeParameters.withIndex()) {
                            val typeArg = typeArgs[IrTypeParameterName(typeParam.name)]!!.second
                            val rendered = when (typeArg.variance) {
                                IrVariance.INVARIANT -> printType(typeArg.type ?: IrAny)
                                IrVariance.COVARIANT -> "out ${printType(typeArg.type ?: IrAny)}"
                                IrVariance.CONTRAVARIANT -> "in ${printType(typeArg.type ?: IrAny)}"
                                IrVariance.STAR -> "*"
                            }
                            sb.append(rendered)
                            if (index != irType.classDecl.typeParameters.lastIndex) {
                                sb.append(", ")
                            }
                        }
                        sb.append(">")
                        sb.toString()
                    }
                }

            is IrTypeParameter -> if (typeContext != TypeParameterDeclaration) {
                irType.name
            } else {
                "${irType.name} : ${printType(irType.upperbound)}"
            }

            /** ```kt
             * class A<T: Any?> {
             *     fun foo(t: T!)
             * }
             * ```
             * `A<Any>::foo::t` is `Any`, not `Any!`
             * `A<Any?>::foo::t` is `Any?!` and is `Any!` in Java but is `Any?` in Kotlin.
             */
            is IrPlatformType -> printTypeDirectly(
                irType.innerType,
                typeContext,
                noNullabilityAnnotation
            )

            is IrDefinitelyNotNullType -> printTypeDirectly(
                irType.innerType,
                typeContext,
                noNullabilityAnnotation
            ) + " & Any"

            else -> throw NoWhenBranchMatchedException()
        }
    }

    override fun IrClassDeclaration.printExtendList(superType: IrType?, implList: List<IrType>): String {
        val sb = if (superType != null || implList.isNotEmpty()) {
            StringBuilder(" ")
        } else {
            StringBuilder()
        }
        if (superType != null || implList.isNotEmpty()) {
            sb.append(": ")
        }
        if (superType != null) {
            sb.append(printType(superType))
            sb.append("()")
            if (implList.isNotEmpty()) {
                sb.append(", ")
            }
        }
        if (implList.isNotEmpty()) {
            for ((index, type) in implList.withIndex()) {
                sb.append(printType(type))
                if (index != implList.lastIndex) {
                    sb.append(", ")
                }
            }
        }
        return sb.toString()
    }

    override fun printIrClassType(irClassType: ClassKind): String {
        return when (irClassType) {
            ABSTRACT -> "abstract class "
            INTERFACE -> "interface "
            OPEN -> "open class "
            FINAL -> "class "
        }
    }

    override fun visitClassDeclaration(clazz: IrClassDeclaration, data: StringBuilder) {
        val topLevel = elementStack.none { it is IrClassDeclaration }
        data.append(indent)
        data.append(kotlinVisibility(clazz.visibility, topLevel))
        data.append(printIrClassType(clazz.classKind))
        data.append(clazz.name)
        val typeParameters = clazz.typeParameters
        if (typeParameters.isNotEmpty()) {
            data.append("<")
            for ((index, typeParameter) in typeParameters.withIndex()) {
                data.append(printType(typeParameter, typeContext = TypeContext.TypeParameterDeclaration))
                if (index != typeParameters.lastIndex) {
                    data.append(", ")
                }
            }
            data.append(">")
        }
        data.append(clazz.printExtendList(clazz.superType, clazz.implementedTypes))
        data.append(" {\n")

        super.visitClassDeclaration(clazz, data)

        data.append(indent)
        data.append("}\n")
    }

    override fun visitFunctionDeclaration(function: IrFunctionDeclaration, data: StringBuilder) {
        if (function.isOverrideStub && !printStub) {
            return
        }
        elementStack.push(function)
        if (function.isOverrideStub) {
            data.append(indent)
            data.append("// stub\n")
            data.append(indent)
            data.append("/*\n")
        }
        data.append(indent)
        if (function.visibility != Visibility.PUBLIC ||
            (function.isOverride && function.override.any { it.visibility != Visibility.PUBLIC })
        ) {
            // An override inherits its base visibility unless it widens it explicitly.
            // Preserve a public IR override when any overridden declaration is non-public.
            data.append(kotlinVisibility(function.visibility, topLevel = function.topLevel))
        }
        if (function.body == null) {
            data.append("abstract ")
        }
        if (function.isOverride) {
            data.append("override ")
        }
        if (!function.isFinal && !function.topLevel) {
            data.append("open ")
        }
        data.append("fun ")
        if (function.typeParameters.isNotEmpty()) {
            data.append("<")
            for ((index, typeParam) in function.typeParameters.withIndex()) {
                data.append(typeParam.name)
                data.append(": ")
                data.append(
                    printType(
                        typeParam.upperbound,
                        TypeContext.FunctionTypeParameterUpperBound
                    )
                )
                if (index != function.typeParameters.lastIndex) {
                    data.append(", ")
                }
            }
            data.append(">")
        }
        data.append(function.name)
        data.append("(")
        visitParameterList(function.parameterList, data)
        data.append("): ")
        data.append(printType(function.returnType, typeContext = TypeContext.ReturnType))
        val body = function.body
        if (body != null) {
            data.append(" {\n")
            indentCount++
            elementStack.push(function)
            visitBlock(body, data)
            require(elementStack.pop() === function)
            indentCount--
            data.append(indent)
            data.append("}")
        }
        data.append("\n")
        if (function.isOverrideStub) {
            data.append(indent)
            data.append("*/\n")
        }
        require(elementStack.pop() === function)
    }

    private fun kotlinVisibility(visibility: Visibility, topLevel: Boolean): String = when (visibility) {
        Visibility.PUBLIC -> "public "
        Visibility.PROTECTED -> if (topLevel) error("Top-level Kotlin declarations cannot be protected") else "protected "
        Visibility.INTERNAL -> "internal "
        Visibility.PRIVATE -> "private "
        Visibility.PACKAGE_PRIVATE -> error("Kotlin has no package-private visibility")
    }

    override fun visitParameterList(parameterList: IrParameterList, data: StringBuilder) {
        val parameters = parameterList.parameters
        for ((index, parameter) in parameters.withIndex()) {
            data.append(parameter.name)
            data.append(": ")
            data.append(printType(parameter.type, typeContext = TypeContext.Parameter))
            if (index != parameters.lastIndex) {
                data.append(", ")
            }
        }
    }

    /*override fun visitProperty(property: IrPropertyDeclaration, data: StringBuilder) {
        data.append(indent)
        if (!property.isFinal) {
            data.append("open ")
        }
        val valOrVar = if (property.readonly) {
            "val "
        } else {
            "var "
        }
        data.append(valOrVar)
        data.append(property.name)
        data.append(": ")
        data.append(printType(property.type))
        if (property.topLevel) {
            data.append(" get()")
        }
        data.append(" = ")
        data.append("TODO()")
        data.append("\n")
    }*/

    override fun visitBlock(block: IrBlock, data: StringBuilder) {
        val function = elementStack.peek() as IrFunctionDeclaration
        if (block.expressions.isEmpty()) {
            data.append(indent)
            if (emitExecutableDefaults) {
                data.append(executableReturnStatement(function))
                data.append("\n")
            } else {
                data.append("throw UnsupportedOperationException()\n")
            }
        } else {
            require(function.returnType === IrUnit /*|| block.expressions.last() is IrReturnExpression*/)
        }
        for (expression in block.expressions) {
            data.append(indent)
            expression.accept(this, data)
            data.append("\n")
        }
    }

    /*override fun visitNewExpression(newExpression: IrNew, data: StringBuilder) {
        data.append(printType(newExpression.createType))
        data.append("()")
    }

    override fun visitFunctionCallExpression(functionCall: IrFunctionCall, data: StringBuilder) {
        val receiver = functionCall.receiver
        val target = functionCall.target
        if (receiver != null) {
            receiver.accept(this, data)
            data.append(".")
        } else if (target.language == Language.JAVA && target.topLevel) {
            data.append(JavaIrClassPrinter.TOP_LEVEL_CONTAINER_CLASS_NAME)
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
        data.append("TODO()")
    }*/

    /**
     * An anonymous subclass of an abstract class or an interface, overriding every abstract
     * member with its default value.
     *
     * An abstract class needs its constructor called (`object : Abs() { }`), an interface must
     * not have one (`object : Iface { }`). Getting this wrong reports
     * `SUPERTYPE_NOT_INITIALIZED: This type has a constructor, so it must be initialized here`.
     *
     * @param baseIndent the indentation of the line the expression starts on.
     * @param depth the current nesting depth of anonymous subclasses.
     */
    fun printAnonymousSubclass(
        default: DefaultValue.AnonymousSubclass,
        baseIndent: String,
        depth: Int = 0,
    ): String {
        if (depth >= DefaultValueSupport.MAX_ANONYMOUS_SUBCLASS_DEPTH) {
            return "throw ${DefaultValueSupport.UNAVAILABLE_EXCEPTION}()"
        }
        val superType = anonymousSuperType(default)
        if (default.members.isEmpty()) {
            return "object : $superType {}"
        }
        val body = default.members.joinToString("\n") { member ->
            printMemberOverride(member, default, depth + 1)
        }
        val indentedBody = body.lines().joinToString("\n") { "$baseIndent    $it" }
        return "object : $superType {\n$indentedBody\n$baseIndent}"
    }

    /**
     * The supertype of the anonymous subclass, with its type arguments.
     */
    private fun anonymousSuperType(default: DefaultValue.AnonymousSubclass): String {
        val typeArguments = if (default.typeArguments.isEmpty()) {
            ""
        } else {
            "<" + default.typeArguments.joinToString(", ") { printType(it) } + ">"
        }
        val name = "${default.classDecl.name}$typeArguments"
        return if (default.hasConstructor) "$name()" else name
    }

    /**
     * Renders one member of an anonymous subclass, overriding it with its default value.
     *
     * The type parameter bounds must be repeated, because an override with different bounds does
     * not implement the abstract member.
     *
     * If the member belongs to the supertype class (which has type parameters),
     * we can't safely override it because its signature references those type parameters.
     * Just throw the exception.
     */
    fun printMemberOverride(
        member: IrFunctionDeclaration,
        default: DefaultValue.AnonymousSubclass,
        depth: Int,
    ): String {
        // If the member belongs to the supertype class (which has type parameters),
        // we can't safely override it because its signature references those type parameters.
        // Just throw the exception.
        val typeArgMap = buildMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>> {
            default.typeArguments.forEachIndexed { index, typeArg ->
                val typeParameter = default.classDecl.typeParameters[index]
                put(IrTypeParameterName(typeParameter.name), typeParameter to typeArg)
            }
        }

        if (member.containingClassName == default.classDecl.name) {
            val returnType = getActualTypeFromArguments(member.returnType, typeArgMap, true)
            val paramTypes = member.parameterList.parameters.map { p ->
                getActualTypeFromArguments(p.type, typeArgMap, true)
            }
            if (returnType !is IrTypeParameter && paramTypes.any { p -> p.containsTypeParameter() }) {
                return buildString {
                    append("override fun ")
                    if (member.typeParameters.isNotEmpty()) {
                        append(member.typeParameters.joinToString(prefix = "<", postfix = ">") { tp ->
                            "${tp.name}: ${printType(tp.upperbound)}"
                        })
                    }
                    append("${member.name}(")
                    append(member.parameterList.parameters.joinToString(", ") { p ->
                        "${p.name}: ${printType(p.type, typeContext = TypeContext.Parameter)}"
                    })
                    append("): ${printType(returnType, typeContext = TypeContext.ReturnType)} = ")
                    append("throw ${DefaultValueSupport.UNAVAILABLE_EXCEPTION}()")
                }
            }
        }

        // Also skip if the member's class is a supertype of the anonymous subclass.
        // The member's signature may reference the supertype's type parameters,
        // which are not in scope in the anonymous subclass.
        val supertypeNames = mutableSetOf<String>()
        default.classDecl.superType?.let { it.classDeclarationOrNull()?.name?.also { supertypeNames.add(it) } }
        default.classDecl.implementedTypes
            .mapNotNull { it.classDeclarationOrNull()?.name }
            .forEach { supertypeNames.add(it) }
        if (member.containingClassName in supertypeNames) {
            val returnType = getActualTypeFromArguments(member.returnType, typeArgMap, true)
            val paramTypes = member.parameterList.parameters.map { p ->
                getActualTypeFromArguments(p.type, typeArgMap, true)
            }
            if (returnType !is IrTypeParameter && paramTypes.any { p -> p.containsTypeParameter() }) {
                return buildString {
                    append("override fun ")
                    if (member.typeParameters.isNotEmpty()) {
                        append(member.typeParameters.joinToString(prefix = "<", postfix = ">") { tp ->
                            "${tp.name}: ${printType(tp.upperbound)}"
                        })
                    }
                    append("${member.name}(")
                    append(member.parameterList.parameters.joinToString(", ") { p ->
                        "${p.name}: ${printType(p.type, typeContext = TypeContext.Parameter)}"
                    })
                    append("): ${printType(returnType, typeContext = TypeContext.ReturnType)} = ")
                    append("throw ${DefaultValueSupport.UNAVAILABLE_EXCEPTION}()")
                }
            }
        }

        // Safe case: member is from a different class (e.g., a mixin/interface not equal to supertype)
        // or we're not dealing with the supertype's type parameters directly.
        val typeParameters = if (member.typeParameters.isEmpty()) {
            ""
        } else {
            member.typeParameters.joinToString(prefix = "<", postfix = ">") { typeParameter ->
                "${typeParameter.name}: ${printType(typeParameter.upperbound)}"
            }
        }
        val parameters = member.parameterList.parameters.joinToString(", ") { parameter ->
            "${parameter.name}: ${printType(parameter.type, typeContext = TypeContext.Parameter)}"
        }
        val returnTypeResolved = getActualTypeFromArguments(member.returnType, typeArgMap, true)
        val paramTypesResolved = member.parameterList.parameters.map { p ->
            getActualTypeFromArguments(p.type, typeArgMap, true)
        }
        val returnType = if (returnTypeResolved is IrTypeParameter || paramTypesResolved.any { p -> p.containsTypeParameter() }) {
            printType(member.returnType, typeContext = TypeContext.ReturnType)
        } else {
            printType(returnTypeResolved, typeContext = TypeContext.ReturnType)
        }
        val value = executableValueExpression(
            returnTypeResolved,
            default.classDecl,
            member,
            depth,
        )
        return "override fun $typeParameters${member.name}($parameters): $returnType = $value"
    }

    /** The statement that returns a value of the function's declared return type. */
    private fun executableReturnStatement(function: IrFunctionDeclaration): String {
        val value = DefaultValueSupport.defaultValue(function.returnType, lastClass, function)
        return when (value) {
            is DefaultValue.Unavailable -> "throw ${DefaultValueSupport.UNAVAILABLE_EXCEPTION}()"
            is DefaultValue.Null -> "return null"
            is DefaultValue.UnitValue -> "return Unit"
            is DefaultValue.AnyValue -> "return Any()"
            is DefaultValue.Constructor -> "return ${constructorCallExpression(value)}"
            is DefaultValue.AnonymousSubclass -> "return ${printAnonymousSubclass(value, indent)}"
        }
    }

    /** The expression that creates an instance of a concrete class. */
    private fun constructorCallExpression(value: DefaultValue.Constructor): String {
        if (value.typeArguments.isEmpty()) return "${value.classDecl.name}()"
        val typeArguments = value.typeArguments.joinToString(", ") { typeArgument ->
            printType(typeArgument, typeContext = TypeContext.TypeArgument)
        }
        return "${value.classDecl.name}<$typeArguments>()"
    }

    /** A value expression for an anonymous-subclass member body. */
    private fun executableValueExpression(
        type: IrType,
        owner: IrClassDeclaration?,
        function: IrFunctionDeclaration?,
        depth: Int,
    ): String = when (val value = DefaultValueSupport.defaultValue(type, owner, function, depth)) {
        is DefaultValue.Unavailable -> "throw ${DefaultValueSupport.UNAVAILABLE_EXCEPTION}()"
        is DefaultValue.Null -> "null"
        is DefaultValue.UnitValue -> "Unit"
        is DefaultValue.AnyValue -> "Any()"
        is DefaultValue.Constructor -> constructorCallExpression(value)
        is DefaultValue.AnonymousSubclass -> printAnonymousSubclass(value, indent, depth)
    }
}
