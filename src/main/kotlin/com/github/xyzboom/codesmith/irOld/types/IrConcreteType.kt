package com.github.xyzboom.codesmith.irOld.types

abstract class IrConcreteType: IrType {
    abstract val arguments: List<IrTypeArgument>

    /**
     * Recursively check if there is any [IrTypeParameter] in [arguments]
     */
    fun unfinishedType(): Boolean {
        return arguments.any {
            when (it) {
                is IrStarProjection -> false
                is IrType -> when(it) {
                    is IrConcreteType -> it.unfinishedType()
                }
                is IrTypeParameter -> true
                is IrTypeProjection -> false
            }
        }
    }

    val unfinishedTypeParameters: List<IrTypeParameter>
        get() = HashSet<IrTypeParameter>().apply {
            for (arg in arguments) {
                if (arg is IrTypeParameter) {
                    add(arg)
                } else if (arg is IrConcreteType) {
                    addAll(arg.unfinishedTypeParameters)
                }
            }
        }.toList()
}