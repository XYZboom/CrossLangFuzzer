package com.github.xyzboom.codesmith.validator

import com.github.xyzboom.codesmith.ir.IrElement
import com.github.xyzboom.codesmith.ir.render

class InvalidElement(val element: IrElement, val extra: String? = null) : IValidatorMessage {
    override fun toString(): String {
        return element.render() + " is invalid." + if (extra != null) {
            " $extra"
        } else ""
    }
}