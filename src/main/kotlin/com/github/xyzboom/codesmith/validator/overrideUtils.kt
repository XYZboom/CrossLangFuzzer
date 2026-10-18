package com.github.xyzboom.codesmith.validator

import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.classes
import com.github.xyzboom.codesmith.ir.declarations.FunctionSignatureMap
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.Signature
import com.github.xyzboom.codesmith.ir.declarations.SuperAndIntfFunctions

import com.github.xyzboom.codesmith.ir.declarations.traceFunc
import com.github.xyzboom.codesmith.ir.declarations.traverseOverride
import com.github.xyzboom.codesmith.ir.types.IrClassifier
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.getActualTypeFromArguments
import com.github.xyzboom.codesmith.ir.types.getConcreteTypeArguments
import com.github.xyzboom.codesmith.ir.types.isSubClassOf
import com.github.xyzboom.codesmith.ir.types.render
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * collect a map whose value is a set of function inherited directly from the supers
 * and whose key is the signature of whose value.
 */
fun IrClassDeclaration.collectFunctionSignatureMap(
    exactSignatures: Boolean = false,
): FunctionSignatureMap {
    logger.trace { "start collectFunctionSignatureMap for class: $name" }
    val result = mutableMapOf<Signature,
            Pair<IrFunctionDeclaration?, MutableSet<IrFunctionDeclaration>>>()
    //           ^^^^^^^^^^^^^^^^^^^^^ decl in super
    //           functions in interfaces ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    for (superType in implementedTypes) {
        superType as? IrClassifier ?: continue
        for (func in superType.classDecl.functions) {
            val signature = func.signatureIn(superType, exactSignatures)
            result.getOrPut(signature) { null to mutableSetOf() }.second.add(func)
        }
    }
    for ((_, pair) in result) {
        val (_, funcs) = pair
        val willRemove = mutableSetOf<IrFunctionDeclaration>()
        for (func in funcs) {
            var found = false
            func.traverseOverride {
                if (it in funcs) {
                    found = true
                    logger.trace {
                        val sb = StringBuilder("found a override in collected that is: ")
                        sb.traceFunc(it)
                        sb.toString()
                    }
                    willRemove.add(it)
                }
            }
            if (found) {
                logger.trace {
                    val sb = StringBuilder("found a override, will remove. For function: ")
                    sb.traceFunc(func)
                    sb.toString()
                }
            }
        }
        funcs.removeAll(willRemove)
    }
    val superType = superType
    if (superType is IrClassifier) {
        for (function in superType.classDecl.functions) {
            val signature = function.signatureIn(superType, exactSignatures)
            val pair = result[signature]
            if (pair == null) {
                result[signature] = function to mutableSetOf()
            } else {
                result[signature] = function to pair.second
            }
        }
    }
    logger.trace { "end collectFunctionSignatureMap for class: $name" }
    return result
}

private fun IrFunctionDeclaration.signatureIn(
    ownerType: IrClassifier,
    exactSignature: Boolean,
): Signature {
    val typeArguments = (ownerType as? IrParameterizedClassifier)
        ?.getConcreteTypeArguments()
        .orEmpty()
    return Signature(
        name,
        parameterList.parameters.map { parameter ->
            getActualTypeFromArguments(parameter.type, typeArguments, true)
        },
        compareParameterTypes = exactSignature,
    )
}

fun IrClassDeclaration.getOverrideCandidates(
    signatureMap: FunctionSignatureMap,
    doNotEditLanguage: Boolean = false
): Triple<MutableList<SuperAndIntfFunctions>, MutableList<SuperAndIntfFunctions>,
        MutableList<SuperAndIntfFunctions>> {
    val mustOverrides = mutableListOf<SuperAndIntfFunctions>()
    val canOverride = mutableListOf<SuperAndIntfFunctions>()
    val stubOverride = mutableListOf<SuperAndIntfFunctions>()
    for ((signature, pair) in signatureMap) {
        val (superFunction, functions) = pair
        logger.debug { "name: ${signature.name}" }
        logger.trace { "parameter: (${signature.parameterTypes.joinToString { it.render() }})" }
        logger.trace {
            val sb = StringBuilder("super function: \n")
            if (superFunction != null) {
                sb.append("\t\t")
                sb.traceFunc(superFunction)
                sb.append("\n")
            } else {
                sb.append("\t\tnull\n")
            }

            sb.append("intf functions: \n")

            for (function in functions) {
                sb.append("\t\t")
                sb.traceFunc(function)
                sb.append("\n")
            }
            sb.toString()
        }

        val nonAbstractCount = functions.count { it.body != null }
        logger.debug { "nonAbstractCount: $nonAbstractCount" }
        var notMustOverride = true
        if (superFunction == null) {
            if (functions.size > 1 || nonAbstractCount != 1) {
                //             ^^^ conflict in intf
                //                    abstract in intf ^^^^
                logger.debug { "must override because [conflict or all abstract] and no final" }
                mustOverrides.add(pair)
                notMustOverride = false
            }
        } else if (superFunction.isFinal) {
            if (nonAbstractCount > 0) {
                logger.trace {
                    "final conflict and could not override, change to Java. doNotEditLanguage: $doNotEditLanguage"
                }
                if (!doNotEditLanguage) {
                    language = Language.JAVA
                }
            }
            stubOverride.add(pair)
            notMustOverride = false
        } else if (superFunction.body == null) {
            mustOverrides.add(pair)
            notMustOverride = false
        } else if (nonAbstractCount > 0) {
            val superOverrides = mutableSetOf<IrFunctionDeclaration>()
            superFunction.traverseOverride { superOverrides.add(it) }
            // If the superFunction already overrides all interface functions, there is no conflict
            val unoverriddenIntfFunctions = functions.filter { it.body != null && it !in superOverrides }
            if (unoverriddenIntfFunctions.isNotEmpty()) {
                logger.debug { "must override because super and intf is conflict and no final" }
                mustOverrides.add(pair)
                notMustOverride = false
            }
        } else if (superFunction.isOverrideStub) {
            /**
             * handle such situation:
             * ```kotlin
             * interface I0 {
             *     fun func() {}
             * }
             * interface I1: I0 {
             *     abstract override fun func()
             * }
             * class P: I0
             * class C: P(), I1
             * ```
             * A stub of 'func' will be generated in class 'P',
             * but 'func' in 'I1' actually override it, so we should do a must override for 'func'.
             * And this situation:
             * ```kotlin
             * interface I0 {
             *     fun func() {}
             * }
             * open class P: I0
             * interface I1 {
             *     fun func()
             * }
             * class C: P(), I1
             * ```
             * A stub of 'func' will be generated in class 'P',
             * but 'func' in 'I1' conflict with the one in 'P'(actually 'I0'),
             * so we should do a must override for 'func'.
             */
            val nonStubOverrides = mutableSetOf<IrFunctionDeclaration>()
            // collect all non stubs
            superFunction.traverseOverride {
                if (!it.isOverrideStub) {
                    nonStubOverrides.add(it)
                }
            }

            for (func in functions) {
                if (!func.isOverrideStub) {
                    nonStubOverrides.add(func)
                } else {
                    func.traverseOverride {
                        if (!it.isOverrideStub) {
                            nonStubOverrides.add(it)
                        }
                    }
                }
            }
            val nonStubNonAbstractCount = nonStubOverrides.count { it.body != null }
            if (nonStubOverrides.any { it.body == null } || (nonStubNonAbstractCount > 0 && nonStubOverrides.size > 1)) {
                // abstract in super interface, or conflict confirmed
                mustOverrides.add(pair)
                notMustOverride = false
            }
        }

        logger.trace { "not must override: $notMustOverride" }
        if (notMustOverride) {
            require(pair.first?.isFinal != true)
            require(pair.second.all { !it.isFinal })
            canOverride.add(pair)
        }
    }
    return Triple(mustOverrides, canOverride, stubOverride)
}

fun IrFunctionDeclaration.implements(candidate: SuperAndIntfFunctions): Boolean {
    if (isOverrideStub) return false
    val (superFunction, interfaceFunctions) = candidate
    val allOverrides = mutableSetOf<IrFunctionDeclaration>()
    traverseOverride { allOverrides.add(it) }
    return superFunction in override ||
            (superFunction != null && superFunction in allOverrides) ||
            interfaceFunctions.any { it in override || it in allOverrides }
}

fun IrClassDeclaration.hasMissingRequiredOverride(): Boolean {
    val (mustOverrides, _, _) = getOverrideCandidates(
        collectFunctionSignatureMap(exactSignatures = true),
        doNotEditLanguage = true,
    )
    return mustOverrides.any { candidate ->
        if (functions.any { it.implements(candidate) }) return@any false

        if (classKind == ClassKind.FINAL || classKind == ClassKind.OPEN) {
            return@any true
        }

        val (superFunction, interfaceFunctions) = candidate
        superFunction == null && interfaceFunctions.count { it.body != null } > 1
    }
}

fun IrProgram.hasMissingRequiredOverride(): Boolean {
    return classes.any { it.hasMissingRequiredOverride() }
}

/**
 * Returns whether [function] is required to resolve an inherited abstract
 * member or an inherited implementation conflict in this class.
 */
fun IrProgram.isRequiredOverride(
    containingClass: IrClassDeclaration,
    function: IrFunctionDeclaration,
): Boolean {
    require(function.isOverride)

    if (containingClass.hasMissingRequiredOverride()) return true
    return classes.any { clazz ->
        clazz !== containingClass &&
                clazz.isSubClassOf(containingClass) &&
                clazz.hasMissingRequiredOverride()
    }
}