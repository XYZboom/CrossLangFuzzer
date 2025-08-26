package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.areEqualTypes
import com.github.xyzboom.codesmith.ir.types.render
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult

class IrTypeMatcher(private val ori: IrType) : Matcher<IrType> {
    override fun test(value: IrType): MatcherResult {
        return if (areEqualTypes(ori, value)) {
            MatcherResult(true, { "" }) { "" }
        } else {
            MatcherResult(false, { "IrType should be ${ori.render()} but was ${value.render()}" }) {
                "IrType should not be ${ori.render()} but was ${value.render()}"
            }
        }
    }
}