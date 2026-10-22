package com.github.xyzboom.codesmith.ir.types

enum class IrVariance {
    INVARIANT,
    COVARIANT,
    CONTRAVARIANT,
    STAR;

    val isInvariant: Boolean get() = this == INVARIANT
    val isCovariant: Boolean get() = this == COVARIANT
    val isContravariant: Boolean get() = this == CONTRAVARIANT
    val isStar: Boolean get() = this == STAR
}

data class IrTypeArgument(
    val variance: IrVariance = IrVariance.INVARIANT,
    val type: IrType? = null
) {
    val isInvariant: Boolean get() = variance.isInvariant
    val isCovariant: Boolean get() = variance.isCovariant
    val isContravariant: Boolean get() = variance.isContravariant
    val isStar: Boolean get() = variance.isStar

    init {
        if (variance == IrVariance.STAR) {
            require(type == null) { "Star projection type argument must have null type" }
        } else {
            require(type != null) { "Non-star projection type argument must have non-null type" }
        }
    }

    companion object {
        val STAR = IrTypeArgument(IrVariance.STAR, null)
        fun invariant(type: IrType) = IrTypeArgument(IrVariance.INVARIANT, type)
        fun covariant(type: IrType) = IrTypeArgument(IrVariance.COVARIANT, type)
        fun contravariant(type: IrType) = IrTypeArgument(IrVariance.CONTRAVARIANT, type)
    }
}
