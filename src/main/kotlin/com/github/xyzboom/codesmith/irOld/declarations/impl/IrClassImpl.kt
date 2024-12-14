package com.github.xyzboom.codesmith.irOld.declarations.impl

import com.github.xyzboom.codesmith.irOld.IrAccessModifier
import com.github.xyzboom.codesmith.irOld.declarations.*
import com.github.xyzboom.codesmith.irOld.types.IrClassType
import com.github.xyzboom.codesmith.irOld.types.IrConcreteType
import com.github.xyzboom.codesmith.irOld.types.IrTypeParameter
import java.util.*
import kotlin.collections.ArrayList

open class IrClassImpl(
    override val name: String,
    override val containingDeclaration: IrDeclarationContainer,
    override var accessModifier: IrAccessModifier = IrAccessModifier.PUBLIC,
    override val classType: IrClassType = IrClassType.FINAL,
    final override var superType: IrConcreteType? = null,
    implementedTypes: List<IrConcreteType> = mutableListOf(),
    override val typeParameters: MutableList<IrTypeParameter> = mutableListOf(),
    override val companionObject: IrCompanionObject? = null
): IrClass {
    override val implementedTypes: MutableList<IrConcreteType> = implementedTypes.toMutableList()
    override val functions: MutableList<IrFunction> = ArrayList()
    override val classes: MutableList<IrClass> = ArrayList()
    override val specialConstructor: IrSpecialConstructor? by lazy { // must be called after super is set
        if (superType == null) {
            null
        } else {
            IrSpecialConstructorImpl(this)
        }
    }

    override fun toString(): String {
        return "${accessModifier.name.lowercase(Locale.getDefault())} class $name"
    }
}