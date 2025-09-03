package com.github.xyzboom.codesmith.com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.generator.IrDeclGenerator
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.render
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult

class IrSubTypeMatcher(
    private val generator: IrDeclGenerator,
    private val superType: IrType
) : Matcher<IrType> {
    override fun test(value: IrType): MatcherResult {
        return if (with(generator) {
                value.isSubTypeOf(superType)
            }) {
            MatcherResult(true, { "${value.render()} should be subtype of ${superType.render()}" }) {
                "${value.render()} should not be subtype of ${superType.render()}"
            }
        } else {
            MatcherResult(false, { "${value.render()} should be subtype of ${superType.render()}" }) {
                "${value.render()} should not be subtype of ${superType.render()}"
            }
        }
    }
}