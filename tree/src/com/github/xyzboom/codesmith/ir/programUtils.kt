package com.github.xyzboom.codesmith.ir

import com.github.xyzboom.codesmith.ir.builder.IrProgramBuilder
import com.github.xyzboom.codesmith.ir.builder.buildModule
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrPropertyDeclaration
import kotlin.random.Random

val IrProgram.classes: List<IrClassDeclaration>
    get() = modules.flatMap { it.classes }

val IrProgram.functions: List<IrFunctionDeclaration>
    get() = modules.flatMap { it.functions }

val IrProgram.properties: List<IrPropertyDeclaration>
    get() = modules.flatMap { it.properties }

fun IrProgram.setMajorLanguage(language: Language) {
    for (clazz in classes) {
        if (clazz.language != Language.JAVA) {
            clazz.language = language
        }
    }
    for (func in functions) {
        if (func.language != Language.JAVA) {
            func.language = language
        }
    }
    for (property in properties) {
        if (property.language != Language.JAVA) {
            property.language = language
        }
    }
}

val IrProgramBuilder.classes: MutableList<IrClassDeclaration>
    get() {
        val targetModule = modules.firstOrNull { it.name == "main" }
            ?: modules.firstOrNull()
            ?: buildModule { name = "main" }.also { modules.add(it) }
        return targetModule.classes
    }

val IrProgramBuilder.functions: MutableList<IrFunctionDeclaration>
    get() {
        val targetModule = modules.firstOrNull { it.name == "main" }
            ?: modules.firstOrNull()
            ?: buildModule { name = "main" }.also { modules.add(it) }
        return targetModule.functions
    }

val IrProgramBuilder.properties: MutableList<IrPropertyDeclaration>
    get() {
        val targetModule = modules.firstOrNull { it.name == "main" }
            ?: modules.firstOrNull()
            ?: buildModule { name = "main" }.also { modules.add(it) }
        return targetModule.properties
    }

inline fun IrProgram.forEachClass(action: (clazz: IrClassDeclaration) -> Unit) {
    for (clazz in classes) {
        action(clazz)
    }
}

fun partitionClassesToModules(
    classes: List<IrClassDeclaration>,
    moduleNumRange: IntRange = 1..1,
    random: Random = Random.Default
): IrProgram {
    if (classes.isEmpty()) {
        return buildProgram {
            modules += buildModule { name = "main" }
        }
    }

    val graph: Map<IrClassDeclaration, Set<IrClassDeclaration>> = computeClassDependencyGraph(classes)
    val components = computeTopologicallyOrderedClassComponents(classes, graph)
    if (components.isEmpty()) {
        return buildProgram {
            modules += buildModule { name = "main" }
        }
    }

    val targetCount = if (moduleNumRange.first == moduleNumRange.last) {
        moduleNumRange.first
    } else {
        random.nextInt(moduleNumRange.first, moduleNumRange.last + 1)
    }.coerceAtLeast(1)

    val actualModuleCount = minOf(targetCount, components.size).coerceAtLeast(1)

    val sliceBase = components.size / actualModuleCount
    val remainder = components.size % actualModuleCount
    var currentCompIdx = 0
    val createdModules = mutableListOf<IrModule>()

    for (modIdx in 0 until actualModuleCount) {
        val extra = if (modIdx < remainder) 1 else 0
        val thisSliceSize = sliceBase + extra
        val sliceComponents = components.subList(currentCompIdx, currentCompIdx + thisSliceSize)
        currentCompIdx += thisSliceSize

        val modName = if (actualModuleCount == 1 || modIdx == actualModuleCount - 1) {
            "main"
        } else {
            "m$modIdx"
        }

        val sliceClasses = sliceComponents.flatten()
        val module = buildModule {
            name = modName
            this.classes.addAll(sliceClasses)
        }
        createdModules.add(module)
    }

    val classToModule = HashMap<IrClassDeclaration, IrModule>()
    for (module in createdModules) {
        for (clazz in module.classes) {
            classToModule[clazz] = module
        }
    }

    for (module in createdModules) {
        val depModules = linkedSetOf<IrModule>()
        for (clazz in module.classes) {
            val classDeps: Set<IrClassDeclaration> = graph[clazz] ?: emptySet()
            for (depClass in classDeps) {
                val depModule = classToModule[depClass]
                if (depModule != null && depModule !== module) {
                    depModules.add(depModule)
                }
            }
        }
        val sortedDeps = createdModules.filter { it in depModules }
        module.dependencies.addAll(sortedDeps)
    }

    for (module in createdModules) {
        val transitiveDependencies = linkedSetOf<IrModule>()
        fun collectDependencies(current: IrModule) {
            for (dependency in current.dependencies) {
                if (transitiveDependencies.add(dependency)) {
                    collectDependencies(dependency)
                }
            }
        }
        collectDependencies(module)
        module.dependencies.clear()
        module.dependencies.addAll(createdModules.filter { it in transitiveDependencies })
    }

    return buildProgram {
        modules.addAll(createdModules)
    }
}

fun IrProgram.repartitionModules(
    moduleNumRange: IntRange = 1..1,
    random: Random = Random.Default
): IrProgram {
    val newProgram = partitionClassesToModules(classes, moduleNumRange, random)
    (modules as MutableList).clear()
    (modules as MutableList).addAll(newProgram.modules)
    return this
}
