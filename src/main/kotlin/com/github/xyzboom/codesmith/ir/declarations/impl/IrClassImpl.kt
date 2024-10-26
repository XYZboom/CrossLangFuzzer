package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.IrAccessModifier
import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.declarations.IrDeclarationContainer
import com.github.xyzboom.codesmith.ir.declarations.IrFunction
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.ir.types.IrConcreteType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.impl.IrConcreteTypeImpl
import java.util.*
import kotlin.collections.ArrayList

open class IrClassImpl(
    override val name: String,
    override val containingDeclaration: IrDeclarationContainer,
    override var accessModifier: IrAccessModifier = IrAccessModifier.PUBLIC,
    override val classType: IrClassType = IrClassType.FINAL,
    override var superType: IrConcreteType? = null,
    implementedTypes: List<IrConcreteType> = mutableListOf(),
    override val typeParameters: MutableList<IrTypeParameter> = mutableListOf(),
): IrClass {
    override val implementedTypes: MutableList<IrConcreteType> = implementedTypes.toMutableList()
    override val functions: MutableList<IrFunction> = ArrayList()
    override val classes: MutableList<IrClass> = ArrayList()
    override val type: IrConcreteType
        get() = IrConcreteTypeImpl(name, this, typeParameters, classType = classType)

    override fun toString(): String {
        return "${accessModifier.name.lowercase(Locale.getDefault())} class $name"
    }
}