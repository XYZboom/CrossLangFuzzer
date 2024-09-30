package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.ir.IrAccessModifier.INTERNAL
import com.github.xyzboom.codesmith.ir.IrAccessModifier.PUBLIC
import com.github.xyzboom.codesmith.ir.declarations.*
import com.github.xyzboom.codesmith.ir.declarations.builtin.BuiltinClasses
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrClassType.*
import kotlin.math.roundToInt
import kotlin.random.Random

class IrGeneratorImpl(
    private val random: Random = Random.Default,
    private val config: GeneratorConfig = GeneratorConfig.default
): IrGenerator {

    private val generatedNames = mutableSetOf<String>()

    override val IrFile.accessibleClasses: Set<IrClass>
        get() = mutableSetOf<IrClass>().apply {
            addAll(BuiltinClasses.builtins)
            addAll(declarations.filterIsInstance<IrClass>())
            addAll(containingPackage.accessibleClasses)
            addAll(containingPackage.containingModule.accessibleClasses)
            addAll(containingPackage.containingModule.program.modules.flatMap { it.accessibleClasses })
        }

    override val IrPackage.accessibleClasses: Set<IrClass>
        get() = mutableSetOf<IrClass>().apply {
            addAll(BuiltinClasses.builtins)
            addAll(files.flatMap {
                it.declarations.filterIsInstance<IrClass>().filter { it1 -> it1.accessModifier == PUBLIC }
            })
        }

    override val IrModule.accessibleClasses: Set<IrClass>
        get() = mutableSetOf<IrClass>().apply {
            addAll(BuiltinClasses.builtins)
            addAll(packages.flatMap { p ->
                p.files.flatMap {
                    it.declarations.filterIsInstance<IrClass>().filter { it1 -> it1.accessModifier == PUBLIC }
                }
            })
        }

    override val IrProgram.accessibleClasses: Set<IrClass>
        get() = mutableSetOf<IrClass>().apply {
            addAll(BuiltinClasses.builtins)
            addAll(modules.flatMap { m ->
                m.packages.flatMap { p ->
                    p.files.flatMap {
                        it.declarations.filterIsInstance<IrClass>().filter { it1 -> it1.accessModifier == PUBLIC }
                    }
                }
            })
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
        if (generatedNames.contains(result)) {
            return randomName(startsWithUpper)
        }
        return result.apply(generatedNames::add)
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
            file { generateClasses() }
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
            }
        }
    }

    override fun IrClass.generateConstructors(num: Int) {

    }
}