package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.builder.IrProgramBuilder
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.containers.IrClassContainer
import com.github.xyzboom.codesmith.ir.containers.IrFuncContainer
import com.github.xyzboom.codesmith.ir.containers.builder.IrTypeParameterContainerBuilder
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.IrClassDeclarationBuilder
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.types.IrClassifier
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.IrTypeParameterName
import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.ALL_BUILTINS
import com.github.xyzboom.codesmith.ir.types.type
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builtin.IrUnit
import com.github.xyzboom.codesmith.ir.types.copy
import com.github.xyzboom.codesmith.ir.types.equalsIgnoreTypeArguments
import com.github.xyzboom.codesmith.ir.types.getTypeArguments
import com.github.xyzboom.codesmith.ir.types.putAllTypeArguments
import com.github.xyzboom.codesmith.ir.types.putTypeArgument
import com.github.xyzboom.codesmith.utils.choice
import com.github.xyzboom.codesmith.utils.nextBoolean
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.random.Random

class IrDeclGenerator(
    private val config: GeneratorConfig = GeneratorConfig.default,
    internal val random: Random = Random.Default,
    private val majorLanguage: Language = Language.KOTLIN
) {
    private val logger = KotlinLogging.logger {}

    private val generatedNames = mutableSetOf<String>().apply {
        addAll(KeyWords.java)
        addAll(KeyWords.kotlin)
        addAll(KeyWords.scala)
        addAll(KeyWords.builtins)
        addAll(KeyWords.windows)
    }

    fun randomName(startsWithUpper: Boolean): String {
        val length = config.nameLengthRange.random(random)
        val sb = StringBuilder(
            if (startsWithUpper) {
                "${upperLetters.random(random)}"
            } else {
                "${lowerStartingLetters.random(random)}"
            }
        )
        for (i in 1 until length) {
            sb.append(lettersAndNumbers.random(random))
        }
        val result = sb.toString()
        val lowercase = result.lowercase()
        if (generatedNames.contains(lowercase)) {
            return randomName(startsWithUpper)
        }
        generatedNames.add(lowercase)
        return result
    }

    var typeParameterCount = 0

    fun nextTypeParameterName(): String {
        return "T${typeParameterCount++}"
    }

    fun randomClassKind(): ClassKind {
        return ClassKind.entries.random(random)
    }

    fun randomType(
        from: IrProgramBuilder,
        typeParameterFromClass: List<IrTypeParameter>?,
        typeParameterFromFunction: List<IrTypeParameter>?,
        finishTypeArguments: Boolean,
        filter: (IrType) -> Boolean
    ): IrType? {
        val builtins = ALL_BUILTINS.filter(filter)
        val fromClassDecl = from.classes.map { it.type }.filter(filter)
        val fromClassTypeParameter = typeParameterFromClass?.filter(filter) ?: emptyList()
        val fromFuncTypeParameter = typeParameterFromFunction?.filter(filter) ?: emptyList()
        val allList = arrayOf(
            builtins, fromClassDecl, fromClassTypeParameter, fromFuncTypeParameter
        )
        if (allList.all { it.isEmpty() }) {
            return null
        }
        val result = choice(*allList, random = random)
        if (finishTypeArguments && result is IrParameterizedClassifier) {
            genTypeArguments(from, typeParameterFromClass, result)
        }
        return result.copy()
    }

    fun randomLanguage(): Language {
        if (random.nextBoolean(config.javaRatio)) {
            return Language.JAVA
        }
        return majorLanguage
    }

    fun genProgram(): IrProgram {
        logger.trace { "start gen program" }
        return buildProgram {
            repeat(config.topLevelDeclRange.random(random)) {
                val clazz = genClass(context = this, randomName(true), randomLanguage())
                classes.add(clazz)
            }
            logger.trace { "finish gen program" }
        }
    }

    fun IrTypeParameterContainerBuilder.genTypeParameter() {
        typeParameters.add(buildTypeParameter {
            this.name = nextTypeParameterName()
            this.upperbound = IrAny
        })
    }

    fun IrClassDeclarationBuilder.genSuperTypes(context: IrProgramBuilder) {
        logger.trace { "start gen super types for ${this.name}" }
        val selectedSupers = mutableListOf<IrType>()
        val allSuperArguments: MutableMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>> = mutableMapOf()
        if (classKind != ClassKind.INTERFACE) {
            val superType = randomType(
                context, typeParameterFromClass = null, typeParameterFromFunction = null,
                finishTypeArguments = false
                //                    ^^^^^
                // as we don't want to search type parameters,
                // so typeParameterFromClass and typeParameterFromFunction here is null,
                // but we want to use type parameters as type arguments, so we do it in our own.
            ) {
                (it.classKind == ClassKind.OPEN || it.classKind == ClassKind.ABSTRACT)
            }
            logger.trace { "choose super: $superType" }
            // record superType
            if (superType is IrClassifier) {
                if (superType is IrParameterizedClassifier) {
                    genTypeArguments(context, typeParameters, superType)
                    allSuperArguments.putAll(superType.getTypeArguments())
                }
                logger.trace { "all super type args: $allSuperArguments" }
                recordSelectedSuper(superType, selectedSupers, allSuperArguments)
            }
            this.superType = superType ?: IrAny
        }
        val willAdd = mutableSetOf<IrType>()
        for (i in 0 until config.classImplNumRange.random(random)) {
            logger.trace { "selected supers: $selectedSupers" }
            val now = randomType(context, null, null, false) { consideringType ->
                logger.trace { "considering $consideringType" }
                var superWasSelected = false
                if (consideringType is IrClassifier) {
                    consideringType.classDecl.traverseSuper {
                        if (selectedSupers.any { it1 -> it.equalsIgnoreTypeArguments(it1) }) {
                            logger.trace { "$it was selected." }
                            superWasSelected = true
                            return@traverseSuper false
                        }
                        true
                    }
                }
                val result = consideringType.classKind == ClassKind.INTERFACE
                        && !superWasSelected
                        && willAdd.all { !it.equalsIgnoreTypeArguments(consideringType) }
                logger.trace {
                    "$consideringType ${
                        if (result) {
                            "can"
                        } else {
                            "can't"
                        }
                    } be considered."
                }
                return@randomType result
            }
            if (now == null) break
            if (now is IrClassifier) {
                logger.trace { "add $now into implement interfaces" }
                if (now is IrParameterizedClassifier) {
                    val mayInSuper = selectedSupers.firstOrNull { it.equalsIgnoreTypeArguments(now) }
                    if (mayInSuper == null) {
                        logger.trace { "$now is not appeared in super, use it with generated type args." }
                        genTypeArguments(context, typeParameters, now)
                        allSuperArguments.putAll(now.getTypeArguments())
                    } else {
                        logger.trace { "$now appeared in super, use it directly." }
                        mayInSuper as IrParameterizedClassifier
                        allSuperArguments.putAll(mayInSuper.getTypeArguments())
                        now.putAllTypeArguments(allSuperArguments)
                    }
                }
                recordSelectedSuper(now, selectedSupers, allSuperArguments)
            }
            willAdd.add(now)
        }
        implementedTypes.addAll(willAdd)
        allSuperTypeArguments = HashMap()
        allSuperTypeArguments.putAll(allSuperArguments)
        logger.trace { "finish gen super types for ${this.name}" }
    }

    private fun recordSelectedSuper(
        now: IrClassifier,
        selectedSupers: MutableList<IrType>,
        allSuperArguments: MutableMap<IrTypeParameterName, Pair<IrTypeParameter, IrType>>
    ) {
        logger.trace { "recording $now into selected super" }
        selectedSupers.add(now)
        now.classDecl.traverseSuper {
            if (selectedSupers.all { it1 -> !it.equalsIgnoreTypeArguments(it1) }) {
                logger.trace { "adding $it to selectedSupers" }
                val rawSuper = it.copy()
                if (rawSuper is IrParameterizedClassifier) {
                    rawSuper.putAllTypeArguments(allSuperArguments)
                    /**
                     * Record indirectly use.
                     * GrandParent<T0>
                     * Parent<T1>: GrandParent<T1>
                     * Child<T2>: Parent<T2>
                     * GrandChild<T3>: Child<T3>
                     * When [now] is GrandChild, we first meet Child(T2 [ T3 ]),
                     * and a k-v pair (T2: T3) is recorded into [allSuperArguments].
                     * Then we meet Parent(T1 [ T2 ]), since we already have a (T2: T3) in [allSuperArguments],
                     * a Parent(T1 [ T3 ]) will be successfully recorded into [selectedSupers].
                     * After this, we meet GrandParent(T0 [ T1 ]), that's why we need the following line.
                     * We need to record a (T1: T3).
                     */
                    allSuperArguments.putAll(rawSuper.getTypeArguments())
                }
                selectedSupers.add(rawSuper)
                logger.trace { "added $rawSuper to selectedSupers" }
            }
            true
        }
    }

    /**
     * @param [visitor] return false in [visitor] if want stop
     */
    private fun IrClassDeclaration.traverseSuper(visitor: (IrType) -> Boolean) {
        val superType = superType
        if (superType is IrClassifier) {
            if (!visitor(superType)) return
            superType.classDecl.traverseSuper(visitor)
        }
        for (intf in implementedTypes) {
            if (intf is IrClassifier) {
                if (!visitor(intf)) return
                intf.classDecl.traverseSuper(visitor)
            }
        }
    }

    fun genTypeArguments(
        context: IrProgramBuilder,
        typeParameterFromClass: List<IrTypeParameter>?,
        superType: IrParameterizedClassifier
    ) {
        for (typeParam in superType.classDecl.typeParameters) {
            val chooseType = randomType(context, typeParameterFromClass, null, false) {
                it !is IrParameterizedClassifier // for now, we forbid nested cases
                        && (config.allowUnitInTypeArgument || it !== IrUnit)
            } ?: IrAny // for now, we do not talk about upperbound
            superType.putTypeArgument(typeParam, chooseType)
        }
    }

    fun genClass(
        context: IrProgramBuilder, name: String = randomName(true), language: Language = randomLanguage()
    ): IrClassDeclaration {
        return buildClassDeclaration {
            this.name = name
            this.classKind = randomClassKind()
            this.language = language
            if (random.nextBoolean(config.classHasTypeParameterProbability)) {
                repeat(config.classTypeParameterNumberRange.random(random)) {
                    genTypeParameter()
                }
            }
            genSuperTypes(context)
        }.apply {
            /*for (i in 0 until config.classMemberNumRange.random(random)) {
                genFunction(
                    context,
                    this,
                    classType == IrClassType.ABSTRACT,
                    classType == IrClassType.INTERFACE,
                    null,
                    randomName(false),
                    language
                )
            }
            genOverrides()*/
        }
        /*val classType = randomClassType()
        return IrClassDeclaration(name, classType).apply {
            this.language = language
            context.classes.add(this)
            if (random.nextBoolean(config.classHasTypeParameterProbability)) {
                repeat(config.classTypeParameterNumberRange.random(random)) {
                    genTypeParameter()
                }
            }
            genSuperTypes(context)

            for (i in 0 until config.classMemberNumRange.random(random)) {
                val generator: IrClassMemberGenerator = if (classType != IrClassType.INTERFACE) {
                    randomClassMemberGenerator()
                } else {
                    this@IrDeclGeneratorImplOld::genFunction
                }
                generator(
                    context,
                    this,
                    classType == IrClassType.ABSTRACT,
                    classType == IrClassType.INTERFACE,
                    null,
                    randomName(false),
                    language
                )
            }
            genOverrides()
        }*/
    }

    fun genFunction(
        classContainer: IrClassContainer,
        funcContainer: IrFuncContainer,
        inAbstract: Boolean,
        inIntf: Boolean,
        returnType: IrType?,
        name: String,
        language: Language
    ): IrFunctionDeclaration? {
        // TODO
        return null
        val topLevel = funcContainer is IrProgram
        logger.trace {
            val sb = StringBuilder("gen function $name for ")
            if (funcContainer is IrClassDeclaration) {
                sb.append("class ")
                sb.append(funcContainer.name)
            } else {
                sb.append("program.")
            }
            sb.toString()
        }
        require(!topLevel || returnType !is IrTypeParameter)
        /*return IrFunctionDeclaration(name, funcContainer).apply {
            this.language = language
            funcContainer.functions.add(this)
            if ((!inIntf && (!inAbstract || random.nextBoolean())) || topLevel) {
                body = IrBlock()
                isFinal = when {
                    topLevel -> true
                    funcContainer is IrClassDeclaration && funcContainer.classType != IrClassType.INTERFACE ->
                        !config.noFinalFunction && random.nextBoolean()

                    else -> false
                }
            }
            for (i in 0 until config.functionParameterNumRange.random(random)) {
                parameterList.parameters.add(
                    genFunctionParameter(
                        classContainer,
                        funcContainer as? IrClassDeclaration,
                        this
                    )
                )
            }
            if (returnType == null) {
                genFunctionReturnType(classContainer, funcContainer as? IrClassDeclaration, this)
            } else {
                logger.trace { "use given return type: $returnType" }
                this.returnType = returnType
            }
            require(!topLevel || this.returnType !is IrTypeParameter)
            if (random.nextBoolean(config.printJavaNullableAnnotationProbability)) {
                logger.trace { "make $name print nullable annotations" }
                printNullableAnnotations = true
            }
            val block = body
            *//*if (block != null) {
                repeat(config.functionExpressionNumRange.random(random)) {
                    val expr = genExpression(block, this, classContainer.program)
                    block.expressions.add(expr)
                }
                if (this.returnType !== IrUnit) {
                    val returnExpr = genExpression(block, this, classContainer.program, this.returnType)
                    block.expressions.add(IrReturnExpression(returnExpr))
                } else {
                    block.expressions.add(IrReturnExpression(null))
                }
            }*//*
        }*/
    }

}