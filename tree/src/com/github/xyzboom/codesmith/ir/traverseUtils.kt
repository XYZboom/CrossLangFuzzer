package com.github.xyzboom.codesmith.ir

import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.types.IrClassifier
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeContainer
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Class dependency graph ordered as dependency-first strongly connected components.
 *
 * An edge points from a class to every program class used by its inheritance or
 * function signature. Classes in one component must be compiled in the same module.
 */
val IrProgram.classDependencyGraph: Map<IrClassDeclaration, Set<IrClassDeclaration>>
    get() = computeClassDependencyGraph(classes)

fun computeClassDependencyGraph(classes: Collection<IrClassDeclaration>): Map<IrClassDeclaration, Set<IrClassDeclaration>> {
    val programClasses = classes.toSet()
    return classes.associateWith { clazz ->
        buildSet {
            val visitedTypes = Collections.newSetFromMap(IdentityHashMap<IrType, Boolean>())
            fun collectType(type: IrType?) {
                if (type == null || !visitedTypes.add(type)) return
                when (type) {
                    is IrClassifier -> {
                        if (type.classDecl in programClasses && type.classDecl !== clazz) {
                            add(type.classDecl)
                        }
                        if (type is IrParameterizedClassifier) {
                            type.arguments.values.forEach { (_, argument) -> collectType(argument) }
                        }
                    }
                    is IrTypeContainer -> collectType(type.innerType)
                    is IrTypeParameter -> collectType(type.upperbound)
                }
            }

            collectType(clazz.superType)
            clazz.implementedTypes.forEach(::collectType)
            clazz.allSuperTypeArguments.values.forEach { (_, type) -> collectType(type) }
            clazz.typeParameters.forEach { collectType(it.upperbound) }
            clazz.functions.forEach { function ->
                collectType(function.returnType)
                function.parameterList.parameters.forEach { collectType(it.type) }
                function.typeParameters.forEach { collectType(it.upperbound) }
            }
        }
    }
}

val IrProgram.topologicallyOrderedClassComponents: List<List<IrClassDeclaration>>
    get() = computeTopologicallyOrderedClassComponents(classes, classDependencyGraph)

fun computeTopologicallyOrderedClassComponents(
    classes: List<IrClassDeclaration>,
    dependencies: Map<IrClassDeclaration, Set<IrClassDeclaration>> = computeClassDependencyGraph(classes)
): List<List<IrClassDeclaration>> {

        var nextIndex = 0
        val index = mutableMapOf<IrClassDeclaration, Int>()
        val lowLink = mutableMapOf<IrClassDeclaration, Int>()
        val stack = ArrayDeque<IrClassDeclaration>()
        val onStack = mutableSetOf<IrClassDeclaration>()
        val components = mutableListOf<List<IrClassDeclaration>>()

        fun strongConnect(clazz: IrClassDeclaration) {
            index[clazz] = nextIndex
            lowLink[clazz] = nextIndex
            nextIndex++
            stack.addLast(clazz)
            onStack.add(clazz)

            for (dependency in dependencies.getValue(clazz)) {
                if (dependency !in index) {
                    strongConnect(dependency)
                    lowLink[clazz] = minOf(lowLink.getValue(clazz), lowLink.getValue(dependency))
                } else if (dependency in onStack) {
                    lowLink[clazz] = minOf(lowLink.getValue(clazz), index.getValue(dependency))
                }
            }

            if (lowLink.getValue(clazz) == index.getValue(clazz)) {
                val component = mutableListOf<IrClassDeclaration>()
                do {
                    val member = stack.removeLast()
                    onStack.remove(member)
                    component += member
                } while (member !== clazz)
                components += component.sortedBy(classes::indexOf)
            }
        }

        classes.forEach { if (it !in index) strongConnect(it) }

        val componentOf = buildMap {
            components.forEachIndexed { componentIndex, component ->
                component.forEach { put(it, componentIndex) }
            }
        }
        val componentDependencies = components.indices.associateWith { mutableSetOf<Int>() }
        dependencies.forEach { (clazz, classDependencies) ->
            val from = componentOf.getValue(clazz)
            classDependencies.forEach { dependency ->
                val to = componentOf.getValue(dependency)
                if (from != to) componentDependencies.getValue(from).add(to)
            }
        }

        val remaining = components.indices.toMutableSet()
        return buildList {
            while (remaining.isNotEmpty()) {
                val next = remaining
                    .filter { componentDependencies.getValue(it).none(remaining::contains) }
                    .minBy { componentIndex -> components[componentIndex].minOf(classes::indexOf) }
                add(components[next])
                remaining.remove(next)
            }
        }
    }

val IrProgram.topologicalOrderedClasses: Sequence<IrClassDeclaration>
    get() = topologicallyOrderedClassComponents.asSequence().flatten()
