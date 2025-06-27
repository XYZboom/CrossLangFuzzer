package com.github.xyzboom.codesmith.ir.types.builtin

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.visitors.IrTransformer
import com.github.xyzboom.codesmith.ir.visitors.IrVisitor

sealed class IrBuiltInType : IrType {
    override fun <R, D> acceptChildren(visitor: IrVisitor<R, D>, data: D) {}

    override fun <D> transformChildren(
        transformer: IrTransformer<D>,
        data: D
    ): IrElement {
        return this
    }
}

object IrAny : IrBuiltInType() {
    override val classKind: ClassKind
        get() = ClassKind.OPEN
}
object IrNothing : IrBuiltInType() {
    override val classKind: ClassKind
        get() = ClassKind.FINAL
}
object IrUnit : IrBuiltInType() {
    override val classKind: ClassKind
        get() = ClassKind.FINAL
}

val ALL_BUILTINS = listOf(
    IrAny,
    IrNothing,
    IrUnit
)