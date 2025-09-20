package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.declarations.builder.IrFunctionDeclarationBuilder
import com.github.xyzboom.codesmith.ir.render
import com.github.xyzboom.codesmith.ir.types.IrClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import io.github.oshai.kotlinlogging.KotlinLogging

typealias SuperAndIntfFunctions = Pair<IrFunctionDeclaration?, MutableSet<IrFunctionDeclaration>>
//                                     ^^^^^^^^^^^^^^^^^^^^^ decl in super
//                                     functions in interfaces ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
typealias FunctionSignatureMap = Map<Signature, SuperAndIntfFunctions>

private val logger = KotlinLogging.logger {}

fun IrFunctionDeclarationBuilder.asString(): String {
    return "${if (body == null) "abstract " else ""}fun $name [" +
            "isOverride=$isOverride, isOverrideStub=$isOverrideStub, isFinal=$isFinal]"
}

fun IrFunctionDeclaration.asString(): String {
    return "${if (body == null) "abstract " else ""}fun $name [" +
            "isOverride=$isOverride, isOverrideStub=$isOverrideStub, isFinal=$isFinal]"
}

fun StringBuilder.traceFunc(
    target: IrFunctionDeclaration,
    context: IrClassDeclaration?
) {
    append(target.asString())
    append(" from ")
    if (context != null) {
        append("class ")
        append(context.name)
    }
}

fun StringBuilder.traceFunc(target: IrFunctionDeclaration) {
    append(target.asString())
    append(" from ")
    if (target.containingClassName != null) {
        append("class ")
        append(target.containingClassName)
    }
    append("parameters: ")
    for (param in target.parameterList.parameters) {
        append(param.render())
    }
}

fun IrFunctionDeclaration.traverseOverride(
    visitor: (IrFunctionDeclaration) -> Unit
) {
    logger.trace {
        val sb = StringBuilder("start traverse: ")
        sb.traceFunc(this)
        sb.toString()
    }
    override.forEach {
        visitor(it)
        it.traverseOverride(visitor)
    }
    logger.trace {
        val sb = StringBuilder("end traverse: ")
        sb.traceFunc(this)
        sb.toString()
    }
}

interface RemoveOnlyIterator {
    fun remove()
}

fun IrFunctionDeclaration.postorderTraverseOverride(
    visitor: (IrFunctionDeclaration, RemoveOnlyIterator) -> Unit
) {
    logger.trace {
        val sb = StringBuilder("start traverse: ")
        sb.traceFunc(this)
        sb.toString()
    }
    val it = override.iterator()
    while (it.hasNext()) {
        val f = it.next()
        f.postorderTraverseOverride(visitor)
        visitor(f, object: RemoveOnlyIterator {
            override fun remove() {
                it.remove()
            }
        })
    }
    logger.trace {
        val sb = StringBuilder("end traverse: ")
        sb.traceFunc(this)
        sb.toString()
    }
}

val IrFunctionDeclaration.signature: Signature
    get() = Signature(name, parameterList.parameters.map { it.type })

/**
 * @param [visitor] return false in [visitor] if want stop
 */
fun IrClassDeclaration.traverseSuper(visitor: (IrType) -> Boolean) {
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