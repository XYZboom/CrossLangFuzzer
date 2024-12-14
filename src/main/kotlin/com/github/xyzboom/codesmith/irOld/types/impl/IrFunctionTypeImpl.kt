package com.github.xyzboom.codesmith.irOld.types.impl

import com.github.xyzboom.codesmith.irOld.declarations.IrClass
import com.github.xyzboom.codesmith.irOld.types.*

class IrFunctionTypeImpl(
    name: String,
    declaration: IrClass,
    arguments: List<IrTypeArgument> = emptyList(),
    nullability: Nullability = Nullability.NOT_SPECIFIED,
    classType: IrClassType = IrClassType.FINAL
): IrConcreteTypeImpl(name, declaration, arguments, nullability, classType), IrFunctionTypeMarker {
    override fun copy(nullability: Nullability): IrType {
        return IrFunctionTypeImpl(name, declaration, arguments, nullability, classType)
    }
}