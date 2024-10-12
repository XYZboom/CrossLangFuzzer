package com.github.xyzboom.codesmith.generator.impl

import com.github.xyzboom.codesmith.generator.*
import com.github.xyzboom.codesmith.ir.IrAccessModifier
import com.github.xyzboom.codesmith.ir.IrAccessModifier.*
import com.github.xyzboom.codesmith.ir.declarations.*
import com.github.xyzboom.codesmith.ir.expressions.IrExpression
import com.github.xyzboom.codesmith.ir.expressions.impl.IrConstructorCallExpressionImpl
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrClassType.*
import com.github.xyzboom.codesmith.ir.types.IrFileType
import kotlin.math.roundToInt
import kotlin.random.Random

class IrGeneratorImpl(
    private val random: Random = Random.Default,
    private val config: GeneratorConfig = GeneratorConfig.default
): IrGenerator, IAccessChecker by AccessCheckerImpl() {

    private val generatedNames = mutableSetOf<String>().apply {
        addAll(KeyWords.java)
        addAll(KeyWords.kotlin)
        addAll(KeyWords.builtins)
        addAll(KeyWords.windows)
    }

    override fun IrFile.generateValueArgumentFor(valueParameter: IrValueParameter): IrExpression {
        val paramClass = valueParameter.type.declaration
        if (!isAccessible(paramClass)) throw IllegalStateException()
        val constructors = paramClass.declarations.filterIsInstance<IrConstructor>().filter { isAccessible(it) }
        if (constructors.isEmpty()) throw IllegalStateException("No available constructor")
        val constructor = constructors.random(random)
        val valueArguments = constructor.valueParameters.map { generateValueArgumentFor(it) }
        return IrConstructorCallExpressionImpl(constructor, valueArguments)
    }

    override fun IrClass.generateValueArgumentFor(valueParameter: IrValueParameter): IrExpression {
        val paramClass = valueParameter.type.declaration
        if (!isAccessible(paramClass)) throw IllegalStateException()
        TODO()
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
        return program {
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
                        it1.accessModifier == PUBLIC || it1.accessModifier == PROTECTED ||
                                (it1.containingPackage === this.containingPackage && it1.accessModifier == INTERNAL)
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
                if (chooseType != INTERFACE) {
                    generateConstructors(config.constructorNumRange.random(random))
                }
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
            val chooseModifier = IrAccessModifier.entries.random(random)
            constructor(IrConstructorCallExpressionImpl(superConstructor, emptyList()), chooseModifier)
        }
    }
}