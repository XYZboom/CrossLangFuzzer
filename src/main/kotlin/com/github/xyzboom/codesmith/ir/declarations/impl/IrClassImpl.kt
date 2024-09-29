package com.github.xyzboom.codesmith.ir.declarations.impl

import com.github.xyzboom.codesmith.ir.declarations.IrClass
import com.github.xyzboom.codesmith.ir.declarations.IrFile
import com.github.xyzboom.codesmith.ir.declarations.IrFunction
import com.github.xyzboom.codesmith.ir.types.IrConcreteType
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.impl.IrConcreteTypeImpl

open class IrClassImpl(
    override val name: String,
    override val containingFile: IrFile,
    override val superType: IrConcreteType? = null,
    override val implementedTypes: MutableList<IrConcreteType> = mutableListOf(),
    override val typeParameters: MutableList<IrTypeParameter> = mutableListOf(),
): IrClass {
    override val functions: MutableList<IrFunction> = ArrayList()
    override val classes: MutableList<IrClass> = ArrayList()
    override val type: IrType
        get() = IrConcreteTypeImpl(name, this, typeParameters)
}