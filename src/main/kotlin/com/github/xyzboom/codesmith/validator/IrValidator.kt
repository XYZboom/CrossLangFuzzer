package com.github.xyzboom.codesmith.validator

import com.github.xyzboom.codesmith.ir.ClassKind.*
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.visitors.IrTopDownVisitor
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.containers.IrClassContainer
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
            val superType1 = superType
            if (superType1 != null) {
                validateParentType(superType1, data, false)
                validateType(classContainer, classContainer, superType1, data, this)
            }
            for (intf in implementedTypes) {
                validateParentType(intf, data, true)
                validateType(classContainer, classContainer, intf, data, this)
            }
        }
        super.visitClassDeclaration(classDeclaration, data)
    }

    override fun visitFunctionDeclaration(
        functionDeclaration: IrFunctionDeclaration,
        data: MessageCollector
    ) {
        val prog = currentProg ?: throw IllegalStateException()
        with(functionDeclaration) {
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