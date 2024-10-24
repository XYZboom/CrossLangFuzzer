package com.github.xyzboom.codesmith.generator.impl

import com.github.xyzboom.codesmith.CodeSmithDsl
import com.github.xyzboom.codesmith.checkers.IAccessChecker
import com.github.xyzboom.codesmith.checkers.ISignatureChecker
import com.github.xyzboom.codesmith.checkers.impl.AccessCheckerImpl
import com.github.xyzboom.codesmith.checkers.impl.SignatureCheckerImpl
import com.github.xyzboom.codesmith.generator.*
import com.github.xyzboom.codesmith.ir.IrAccessModifier
import com.github.xyzboom.codesmith.ir.IrAccessModifier.*
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.declarations.*
import com.github.xyzboom.codesmith.ir.declarations.impl.IrConstructorImpl
import com.github.xyzboom.codesmith.ir.declarations.impl.IrValueParameterImpl
import com.github.xyzboom.codesmith.ir.expressions.IrConstructorCallExpression
import com.github.xyzboom.codesmith.ir.expressions.IrExpression
import com.github.xyzboom.codesmith.ir.expressions.impl.IrConstructorCallExpressionImpl
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrClassType.*
import com.github.xyzboom.codesmith.ir.types.IrFileType
import com.github.xyzboom.codesmith.ir.types.Nullability
import kotlin.math.roundToInt
import kotlin.random.Random

class IrGeneratorImpl(
    private val random: Random = Random.Default,
    private val config: GeneratorConfig = GeneratorConfig.default
): IrGenerator, IAccessChecker by AccessCheckerImpl(), ISignatureChecker by SignatureCheckerImpl() {

    private val generatedNames = mutableSetOf<String>().apply {
        addAll(KeyWords.java)
        addAll(KeyWords.kotlin)
        addAll(KeyWords.builtins)
        addAll(KeyWords.windows)
    }

    private val generatedClasses = ArrayList<IrClass>(64)
    private val subclassesCache = HashMap<IrClass, List<IrClass>>(64)

    @CodeSmithDsl
    override fun IrClass.constructor(
        superCall: IrConstructorCallExpression,
        accessModifier: IrAccessModifier,
        valueParameters: MutableList<IrValueParameter>,
        constructorCtx: IrConstructor.() -> Unit
    ): IrConstructor {
        return IrConstructorImpl(accessModifier, this, superCall, valueParameters)
            .apply(constructorCtx).apply {
                if (!this@constructor.containsSameSignature(this)) {
                    this@constructor.functions.add(this)
                }
            }
    }

    //<editor-fold desc="subtyping">
    private val IrClass.subclasses: List<IrClass>
        get() {
            if (subclassesCache.containsKey(this)) {
                return subclassesCache[this]!!
            }
            return ArrayList<IrClass>().apply {
                add(this@subclasses)
                for (clazz in generatedClasses) {
                    if (this@subclasses in clazz.allSuperClasses) {
                        add(clazz)
                    }
                }
                subclassesCache[this@subclasses] = this
            }
        }

    //</editor-fold>

    override fun IrElement.generateValueArgumentFor(valueParameter: IrValueParameter): IrExpression {
        val paramClass = valueParameter.type.declaration
        if (!isAccessible(paramClass)) throw IllegalStateException()
        val constructors = paramClass.subclasses.flatMap { it.declarations }
            .filterIsInstance<IrConstructor>().filter { isAccessible(it) }
        if (constructors.isEmpty()) throw IllegalStateException("No available constructor")
        val constructor = constructors.random(random)
        val valueArguments = constructor.valueParameters.map { generateValueArgumentFor(it) }
        return IrConstructorCallExpressionImpl(constructor, valueArguments)
    }

    override fun randomName(startsWithUpper: Boolean): String {
        val length = config.nameLengthRange.random(random)
        val sb = StringBuilder(
            if (startsWithUpper) {
                "${upperLetters.random(random)}"
            } else {
                "${lowerLetters.random(random)}"
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

    override fun IrProgram.generateModuleDependencies() {
        if (modules.size <= 1) return
        if (modules.size == 2) {
            if (random.nextBoolean()) {
                modules[0].dependsOn(modules[1])
            } else {
                modules[1].dependsOn(modules[0])
            }
        }
        val allModules = ArrayList(modules)
        val needMax = (allModules.size * config.moduleDependencySelectionRate).toInt().coerceAtLeast(2)
        val waitBeDepended = ArrayDeque<IrModule>()
        val iterator = allModules.iterator()
        while (iterator.hasNext()) {
            for (i in 0 until needMax) {
                val next = iterator.next()
                if (waitBeDepended.isNotEmpty()) {
                    waitBeDepended.shuffle(random)
                    val minTake = (waitBeDepended.size * config.moduleDependencyMinRate)
                        .roundToInt().coerceIn(0..<waitBeDepended.size)
                    val maxTake = (waitBeDepended.size * config.moduleDependencyMaxRate)
                        .roundToInt().coerceIn(minTake + 1..waitBeDepended.size)
                    next.dependsOn(waitBeDepended.take(random.nextInt(minTake, maxTake)))
                }
                waitBeDepended.add(next)
                if (!iterator.hasNext()) {
                    break
                }
            }
            val sizeToMove = (waitBeDepended.size * config.moduleDependencyEliminateRate).toInt()
            for (i in 0 until sizeToMove) {
                waitBeDepended.removeFirstOrNull()
            }
        }
    }

    override fun generate(): IrProgram {
        val prog = program {
            for (i in 0 until config.moduleNumRange.random(random)) {
                module()
            }
            generateModuleDependencies()
            for (module in modules) {
                with(module) {
                    var lastPackage: IrPackage? = null
                    for (i in 0 until config.packageNumRange.random(random)) {
                        lastPackage = if (random.nextBoolean()) {
                            `package` {
                                generateFiles()
                            }
                        } else {
                            `package`(parent = lastPackage) {
                                generateFiles()
                            }
                        }
                    }
                }
            }
            mainModule = module {
                dependsOn(modules)
            }
        }
        for (clazz in generatedClasses) {
            with(clazz) {
                if (clazz.classType != INTERFACE) {
                    generateConstructors(config.constructorTryNumRange.random(random))
                }
            }
        }
        return prog
    }

    override fun IrPackage.generateFiles() {
        for (i in 0 until config.fileNumRange.random(random)) {
            file(fileType = IrFileType.entries.random(random)) { generateClasses() }
        }
    }

    override fun IrFile.generateClasses() {
        for (i in 0 until config.classNumRange.random(random)) {
            val chooseType = IrClassType.entries.random(random)
            val chooseModifier = listOf(PUBLIC, INTERNAL).random(random)
            val accessibleClasses = accessibleClasses
            val superType = if (chooseType != INTERFACE) {
                accessibleClasses.filter {
                    it.accessModifier <= chooseModifier && (it.classType == ABSTRACT || it.classType == OPEN)
                            && it.functions.filterIsInstance<IrConstructor>()
                        .any { it1 ->
                            // constructor is accessible
                            (it1.accessModifier == PUBLIC || it1.accessModifier == PROTECTED ||
                                    (it1.containingPackage === this.containingPackage && it1.accessModifier == INTERNAL))
                                    // all value parameter are accessible
                                    && (it1.valueParameters.all { it2 -> isAccessible(it2.type.declaration) })
                        }
                }.random(random)
            } else {
                null
            }
            val interfaces = accessibleClasses.filter {
                it.accessModifier <= chooseModifier && it.classType == INTERFACE
            }.shuffled(random)
            val implements = if (interfaces.isNotEmpty()) {
                interfaces.take(
                    config.classImplNumRange.random(random)
                        .coerceAtMost(interfaces.size - 1)
                ).map { it.type }
            } else {
                emptyList()
            }
            `class`(
                containingFile = this, classType = chooseType, accessModifier = chooseModifier,
                superType = superType?.type, implementedTypes = implements
            ) {
                generatedClasses.add(this)
            }
        }
    }

    override fun IrClass.generateConstructors(num: Int) {
        if (this.classType == INTERFACE) {
            throw IllegalStateException("An interface should not have constructor")
        }
        val superType = (superType
            ?: throw IllegalStateException("A class without super type should not call super constructors"))
        val superTypePackage = superType.declaration.containingPackage
        val samePackage = superTypePackage == this.containingPackage
        val accessibleSuperConstructors = superType.declaration.functions.filterIsInstance<IrConstructor>()
            .filter {
                it.accessModifier == PUBLIC || it.accessModifier == PROTECTED ||
                        (samePackage && it.accessModifier == INTERNAL)
            }
        if (accessibleSuperConstructors.isEmpty()) {
            throw IllegalStateException("The class: ${superType.name} has no constructor")
        }
        val accessibleClasses = when (val containingDeclaration = containingDeclaration) {
            is IrClass -> TODO()
            is IrFile -> containingDeclaration.accessibleClasses
        }
        for (i in 0 until num) {
            val superConstructor = accessibleSuperConstructors.random(random)
            val superValueParameters = superConstructor.valueParameters
            val superValueArguments = superValueParameters.map { generateValueArgumentFor(it) }
            val chooseModifier = IrAccessModifier.entries.random(random)
            val valueParameters = mutableListOf<IrValueParameter>()
            constructor(
                IrConstructorCallExpressionImpl(superConstructor, superValueArguments), chooseModifier,
                valueParameters = valueParameters
            ) {
                for (p in 0 until config.constructorParameterNumRange.random(random)) {
                    valueParameters.add(randomValueParameter())
                }
            }
        }
    }

    override fun IrFunction.randomValueParameter(): IrValueParameter {
        val file = containingFile
        val chooseNullability = when (file.fileType) {
            IrFileType.KOTLIN -> if (random.nextBoolean()) Nullability.NOT_NULL else Nullability.NULLABLE
            IrFileType.JAVA -> Nullability.entries.random(random)
        }
        val chooseClass = file.accessibleClasses.filter { it.accessModifier <= this.accessModifier }.random(random)
        val chooseType = chooseClass.type.copy(chooseNullability)
        return IrValueParameterImpl(randomName(false), chooseType)
    }
}