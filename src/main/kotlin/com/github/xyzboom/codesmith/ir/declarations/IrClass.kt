package com.github.xyzboom.codesmith.ir.declarations

import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrConcreteType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.impl.IrConcreteTypeImpl
import com.github.xyzboom.codesmith.ir.visitor.IrVisitor

interface IrClass: IrDeclaration, IrFunctionContainer, IrDeclarationContainer, IrClassContainer,
    IrAccessModifierContainer {
    val name: String
    val containingDeclaration: IrDeclarationContainer
    val containingFile: IrFile
        get() = when (val containingDeclaration = containingDeclaration) {
            is IrClass -> containingDeclaration.containingFile
            is IrFile -> containingDeclaration
            else -> throw IllegalStateException()
        }
    val classType: IrClassType
    val type: IrConcreteType
        get() = IrConcreteTypeImpl(name, this, typeParameters, classType = classType)
    var superType: IrConcreteType?
    val implementedTypes: MutableList<IrConcreteType>

    /**
     * Used for quickly generating expressions
     */
    val specialConstructor: IrSpecialConstructor?
    val allSuperClasses: Set<IrClass>
        get() = HashSet<IrClass>().apply {
            val superType = superType
            if (superType != null) {
                add(superType.declaration)
                addAll(superType.declaration.allSuperClasses)
            }
            addAll(implementedTypes.map { it.declaration })
            addAll(implementedTypes.flatMap { it.declaration.allSuperClasses })
        }
    val typeParameters: MutableList<IrTypeParameter>
    override val declarations: List<IrDeclaration>
        get() = ArrayList<IrDeclaration>(functions.size + classes.size).apply {
            addAll(functions)
            addAll(classes)
        }

    override fun <R, D> accept(visitor: IrVisitor<R, D>, data: D): R =
        visitor.visitClass(this, data)

    override fun <D> acceptChildren(visitor: IrVisitor<Unit, D>, data: D) {
        declarations.forEach { it.accept(visitor, data) }
    }
}