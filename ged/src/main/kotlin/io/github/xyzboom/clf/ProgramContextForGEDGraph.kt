package io.github.xyzboom.clf

import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.types.*
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType
import io.github.xyzboom.gedlib.GEDEnv
import io.github.xyzboom.gedlib.GEDGraph
import io.github.xyzboom.gedlib.GEDNode

/**
 * Helper class for generate [GEDGraph] from [IrProgram].
 */
internal class ProgramContextForGEDGraph(
    private val env: GEDEnv,
    private val prog: IrProgram
) {
    companion object {
        const val KEY_TYPE = "type"
        const val KEY_TYPE_ARG = "type argument"
    }

    // key: IrClassDeclaration name, value: node
    private val classNodeMap = HashMap<String, GEDNode>()

    // ket: rendered type, value: node
    private val typeMap = HashMap<String, GEDNode>()
    private val typeParameterMap = HashMap<IrTypeParameterName, GEDNode>()
    private val functionMap = HashMap<String, GEDNode>()

    /**
     * As all type parameters has unique names,
     * we can always create one node when we meet a new type parameter.
     */
    fun GEDGraph.addTypeParameter(typeParameter: IrTypeParameter): GEDNode {
        if (typeParameterMap.containsKey(IrTypeParameterName(typeParameter.name))) {
            return typeParameterMap[IrTypeParameterName(typeParameter.name)]!!
        }
        val node = GEDNode(typeParameter.name)
        val properties = HashMap<String, String>()
        properties[KEY_TYPE] = IrTypeParameter::class.simpleName!!
        addNode(node, properties)
        typeParameterMap[IrTypeParameterName(typeParameter.name)] = node
        return node
    }

    /**
     * This is the first stage handling.
     * The super type and implemented types will be added in the second stage.
     */
    fun GEDGraph.addClass(clazz: IrClassDeclaration): GEDNode {
        val classNode = GEDNode(clazz.name)
        val classProperties = HashMap<String, String>()
        classProperties[KEY_TYPE] = IrClassDeclaration::class.simpleName!!
        classProperties[clazz::language.name] = clazz.language.name
        classProperties[clazz::classKind.name] = clazz.classKind.name
        addNode(classNode, classProperties)
        for (typeParameter in clazz.typeParameters) {
            val typeParamNode = addTypeParameter(typeParameter)
            addEdge(classNode, typeParamNode)
        }
        classNodeMap[clazz.name] = classNode
        return classNode
    }

    fun findClassNode(className: String): GEDNode {
        return classNodeMap[className]!!
    }

    fun GEDGraph.addOrFindType(type: IrType): GEDNode {
        val rendered = type.render()
        if (rendered in typeMap) {
            return typeMap[rendered]!!
        }
        val node = GEDNode(rendered)
        val properties = HashMap<String, String>()
        properties[KEY_TYPE] = type::class.simpleName!!
        addNode(node, properties)
        typeMap[rendered] = node
        when (type) {
            is IrNullableType -> {
                val innerTypeNode = addOrFindType(type.innerType)
                addEdge(node, innerTypeNode)
            }

            is IrParameterizedClassifier -> {
                val classNode = findClassNode(type.classDecl.name)
                addEdge(node, classNode)
                for ((paramName, pair) in type.arguments) {
                    val (_, typeArg) = pair
                    if (typeArg == null) {
                        continue
                    }
                    val argNode = addOrFindType(typeArg)
                    addEdge(node, argNode, buildMap {
                        put(KEY_TYPE_ARG, paramName.value)
                    })
                }
            }

            is IrSimpleClassifier -> {
                val classNode = findClassNode(type.classDecl.name)
                addEdge(node, classNode)
            }

            is IrTypeParameter -> {
                val upperBoundNode = addOrFindType(type.upperbound)
                addEdge(node, upperBoundNode, buildMap {
                    put(type::upperbound.name, type::upperbound.name)
                })
            }

            is IrBuiltInType -> {
                // nothing need to add for built-in type
            }

            else -> throw NoWhenBranchMatchedException("Unexpected IrType ${this::class.qualifiedName}")
        }
        return node
    }

    fun GEDGraph.handleClassSupers(clazz: IrClassDeclaration) {
        val classNode = classNodeMap[clazz.name]!!
        val superType = clazz.superType
        if (superType != null) {
            val superTypeNode = addOrFindType(superType)
            addEdge(classNode, superTypeNode, buildMap {
                put(clazz::superType.name, clazz::superType.name)
            })
        }
        for (implType in clazz.implementedTypes) {
            val implTypeNode = addOrFindType(implType)
            addEdge(classNode, implTypeNode, buildMap {
                put(clazz::implementedTypes.name, clazz::implementedTypes.name)
            })
        }
    }

    /**
     * This is the first stage handling.
     * The overridden functions while be handled latter.
     */
    fun GEDGraph.addFunction(function: IrFunctionDeclaration) {
        val containingClassName = function.containingClassName
        val prefix = if (containingClassName != null) "${containingClassName}." else ""
        val functionQualifiedName = "${prefix}${function.name}"
        val returnNodeName = "${functionQualifiedName}.return"

        fun parameterNodeName(index: Int): String {
            return "${functionQualifiedName}.parameters[$index]"
        }

        val node = GEDNode(functionQualifiedName)
        val properties = HashMap<String, String>()

        properties.put(function::language.name, function.language.name)
        properties.put(
            function::body.name, if (function.body != null) {
                "true"
            } else {
                "false"
            }
        )
        properties.put(function::isOverride.name, function.isOverride.toString())
        properties.put(function::isOverrideStub.name, function.isOverrideStub.toString())
        properties.put(function::isFinal.name, function.isFinal.toString())

        addNode(node, properties)
        if (containingClassName != null) {
            val classNode = findClassNode(containingClassName)
            addEdge(classNode, node)
        }
        for (typeParameter in function.typeParameters) {
            val typeParamNode = addTypeParameter(typeParameter)
            addEdge(node, typeParamNode)
        }
        for ((index, param) in function.parameterList.parameters.withIndex()) {
            val parameterNode = GEDNode(parameterNodeName(index))
            addNode(parameterNode)
            addEdge(node, parameterNode)
            val typeNode = addOrFindType(param.type)
            addEdge(parameterNode, typeNode)
        }
        val returnTypeNode = addOrFindType(function.returnType)
        val returnNode = GEDNode(returnNodeName)
        addNode(returnNode)
        addEdge(node, returnNode)
        addEdge(returnNode, returnTypeNode)
        functionMap[functionQualifiedName] = node
    }

    fun GEDGraph.handleOverride(function: IrFunctionDeclaration) {
        val containingClassName = function.containingClassName!!
        val functionQualifiedName = "${containingClassName}.${function.name}"
        val functionNode = functionMap[functionQualifiedName]!!
        for (override in function.override) {
            val overrideQName = "${override.containingClassName!!}.${override.name}"
            val overrideNode = functionMap[overrideQName]!!
            addEdge(functionNode, overrideNode)
        }
    }

    fun toGraph(): GEDGraph {
        val graph = env.addGraph()
        with(graph) {
            for (clazz in prog.classes) {
                addClass(clazz)
            }
            for (clazz in prog.classes) {
                handleClassSupers(clazz)
                for (function in clazz.functions) {
                    addFunction(function)
                }
            }
            for (clazz in prog.classes) {
                for (function in clazz.functions) {
                    handleOverride(function)
                }
            }
        }
        return graph
    }
}