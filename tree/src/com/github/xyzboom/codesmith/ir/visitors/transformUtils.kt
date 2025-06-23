package com.github.xyzboom.codesmith.ir.visitors

import com.github.xyzboom.codesmith.ir.IrElement

fun <T : IrElement, D> MutableList<T>.transformInplace(transformer: IrTransformer<D>, data: D) {
    val iterator = this.listIterator()
    while (iterator.hasNext()) {
        val next = iterator.next()
        val result = next.transform<T, D>(transformer, data)
        if (result !== next) {
            iterator.set(result)
        }
    }
}