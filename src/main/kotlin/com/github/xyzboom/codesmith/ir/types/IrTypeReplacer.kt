package com.github.xyzboom.codesmith.ir.types

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.types.builder.buildDefinitelyNotNullType
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildPlatformType
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer

class IrTypeReplacer(
    private val originalType: IrType,
    private val newType: IrType
) : IrTransformer<Nothing?>() {
    override fun <E : IrElement> transformElement(
        element: E,
        data: Nothing?
    ): E {
        if (element !is IrType) {
            throw IllegalStateException("IrTypeReplacer is only used for IrType.")
        }
        // we make sure the transform only happens on IrType, so this is safe cast
        @Suppress("UNCHECKED_CAST")
        element.accept(this, data)
        return element
    }

    override fun transformType(type: IrType, data: Nothing?): IrType {
        if (areEqualTypes(type, originalType)) {
            return newType.copy()
        }
        type.transformChildren(this, data)
        return type
    }

    /**
     * [originalType] T0
     * [newType] T1?
     * replacement result of `T0?` is `T1?`, not `T1??`
     * [originalType] T0
     * [newType] T1!
     * replacement result of `T0?` is `T1?`, not `T1!?`
     * [originalType] T0
     * [newType] T1 & Any
     * replacement result of `T0?` is `T1?`, not `(T1 & Any)?`
     */
    override fun transformNullableType(nullableType: IrNullableType, data: Nothing?): IrType {
        if (areEqualTypes(nullableType.innerType, originalType)) {
            return buildNullableType { innerType = newType.deepUnwrap().copy() }
        }
        return super.transformNullableType(nullableType, data)
    }

    /**
     * [originalType] T0
     * [newType] T1?
     * replacement result of `T0 & Any` is `T1 & Any` when `T1` is [IrTypeParameter] otherwise `T1`.
     * [originalType] T0
     * [newType] T1!
     * replacement result of `T0 & Any` is `T1 & Any` when `T1` is [IrTypeParameter] otherwise `T1`.
     * [originalType] T0
     * [newType] T1 & Any
     * replacement result of `T0 & Any` is `T1 & Any` when `T1` is [IrTypeParameter] otherwise `T1`.
     */
    override fun transformDefinitelyNotNullType(
        definitelyNotNullType: IrDefinitelyNotNullType,
        data: Nothing?
    ): IrType {
        if (areEqualTypes(definitelyNotNullType.innerType, originalType)) {
            val unwrapped = newType.deepUnwrap().copy()
            return if (unwrapped is IrTypeParameter) {
                buildDefinitelyNotNullType { innerType = unwrapped }
            } else unwrapped
        }
        return super.transformDefinitelyNotNullType(definitelyNotNullType, data)
    }

    /**
     * [originalType] T0
     * [newType] T1?
     * replacement result of `T0!` is `T1?`
     * [originalType] T0
     * [newType] T1!
     * replacement result of `T0!` is `T1!`, not `T1!!`
     * [originalType] T0
     * [newType] T1 & Any
     * replacement result of `T0!` is `T1!`, not `(T1 & Any)!`
     */
    override fun transformPlatformType(platformType: IrPlatformType, data: Nothing?): IrType {
        if (areEqualTypes(platformType.innerType, originalType)) {
            return when (val replaceWith = newType) {
                is IrNullableType -> buildNullableType { innerType = replaceWith }
                is IrPlatformType, is IrDefinitelyNotNullType
                    -> buildPlatformType { innerType = replaceWith.innerType }
                else -> buildPlatformType { innerType = replaceWith }
            }
        }
        return super.transformPlatformType(platformType, data)
    }
}