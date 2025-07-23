package com.github.xyzboom.codesmith.validator

import com.github.xyzboom.codesmith.ir.ClassKind.*
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.visitors.IrTopDownVisitor
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.containers.IrClassContainer
import com.github.xyzboom.codesmith.ir.declarations.SuperAndIntfFunctions
import com.github.xyzboom.codesmith.ir.types.IrClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.render
import kotlin.IllegalStateException

class IrValidator : IrTopDownVisitor<MessageCollector>() {

    fun validate(prog: IrProgram): MessageCollector {
        val messageCollector = MessageCollector()
        visitProgram(prog, messageCollector)
        return messageCollector
    }

    val elementStack = ArrayDeque<IrElement>()
    private val currentProg: IrProgram?
        get() {
            for (ele in elementStack) {
                if (ele is IrProgram) return ele
            }
            return null
        }
    private val currentClass: IrClassDeclaration?
        get() {
            for (ele in elementStack) {
                if (ele is IrClassDeclaration) return ele
            }
            return null
        }

    val classOverrideCandidateStack = ArrayDeque<Pair<IrClassDeclaration,
            Triple<MutableList<SuperAndIntfFunctions>, MutableList<SuperAndIntfFunctions>,
                    MutableList<SuperAndIntfFunctions>>>>()

    override fun visitElement(element: IrElement, data: MessageCollector) {
        elementStack.addFirst(element)
        super.visitElement(element, data)
        require(elementStack.removeFirst() === element)
    }

    fun IrClassDeclaration.validateParentType(
        parentType: IrType,
        collector: MessageCollector,
        expectInterface: Boolean
    ) {
        fun reportUnexpectedParentType(expect: String) {
            collector.report(
                InvalidElement(
                    this,
                    "Unexpect parent type, expect $expect, found ${parentType.render()} as ${classKind.name.lowercase()}"
                )
            )
        }

        when (parentType.classKind) {
            ABSTRACT if expectInterface -> reportUnexpectedParentType("interface")
            OPEN if expectInterface -> reportUnexpectedParentType("interface")
            INTERFACE if !expectInterface -> reportUnexpectedParentType("open or abstract")
            FINAL -> reportUnexpectedParentType("not final")
            else -> {} // success
        }
    }

    fun validateType(
        program: IrProgram,
        classContainerCtx: IrClassContainer,
        type: IrType,
        collector: MessageCollector,
        classCtx: IrClassDeclaration? = null,
        funcCtx: IrFunctionDeclaration? = null
    ) {
        fun reportNoSuchType() {
            collector.report(InvalidElement(type, "No such type."))
        }

        with(classContainerCtx) {} // todo: no nested classes now, so ctx is unused
        when (type) {
            is IrClassifier -> {
                if (type.classDecl !in program.classes) {
                    reportNoSuchType()
                }
            }
        }
    }

    override fun visitClassDeclaration(
        classDeclaration: IrClassDeclaration,
        data: MessageCollector
    ) {
        val classContainer = elementStack.first()
        if (classContainer !is IrClassContainer) {
            data.report(InvalidElement(classDeclaration, "Class Decl should be in a Class Container!"))
            return super.visitClassDeclaration(classDeclaration, data)
        }
        if (classContainer !is IrProgram) {
            // todo we do not support nested class now.
            data.report(InvalidElement(classDeclaration, "Class Decl should be in a Program!"))
            return super.visitClassDeclaration(classDeclaration, data)
        }
        with(classDeclaration) {
            val signatureMap = collectFunctionSignatureMap()
            classOverrideCandidateStack.addFirst(this to getOverrideCandidates(signatureMap))
            val superType1 = superType
            if (superType1 != null) {
                validateParentType(superType1, data, false)
                validateType(classContainer, classContainer, superType1, data, this)
            }
            for (intf in implementedTypes) {
                validateParentType(intf, data, true)
                validateType(classContainer, classContainer, intf, data, this)
            }
            super.visitClassDeclaration(this, data)
            val popOverrideCandidate = classOverrideCandidateStack.removeFirst()
            require(popOverrideCandidate.first === this)
            val (must, can, stub) = popOverrideCandidate.second
            for (superAndIntf in must) {
                val (superFunc, intfFuncs) = superAndIntf
                val first = superFunc ?: intfFuncs.firstOrNull() ?: continue
                data.report(InvalidElement(this, "Class has abstract or conflict function(s): ${first.name}"))
            }
        }
    }

    override fun visitFunctionDeclaration(
        functionDeclaration: IrFunctionDeclaration,
        data: MessageCollector
    ) {
        val prog = currentProg ?: throw IllegalStateException()
        val overrideCandidate = classOverrideCandidateStack.first()
        val (_, triple) = overrideCandidate
        val (must, can, stub) = triple
        with(functionDeclaration) {
            val mustIter = must.iterator()
            while (mustIter.hasNext()) {
                val myMust = mustIter.next()
                myMust.first ?: myMust.second.firstOrNull { it.name == this.name } ?: continue
                if (!isOverrideStub && body != null) {
                    mustIter.remove()
                }
            }
            for (overrideF in override) {
                val overrideFromClass = prog.classes.firstOrNull { it.name == overrideF.containingClassName }
                if (overrideFromClass == null) {
                    data.report(
                        InvalidElement(
                            functionDeclaration,
                            "The function is overriding a function whose class that does not exists. " +
                                    "Overriding at class: ${overrideF.containingClassName}"
                        )
                    )
                    continue
                }
                val override = overrideFromClass.functions.firstOrNull { it.name == overrideF.name }
                if (override == null) {
                    data.report(
                        InvalidElement(
                            functionDeclaration,
                            "The function is overriding a function that does not exists. " +
                                    "Overriding at class: ${overrideF.containingClassName}"
                        )
                    )
                    continue
                }
            }
        }

        super.visitFunctionDeclaration(functionDeclaration, data)
    }
}