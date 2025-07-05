package com.github.xyzboom.codesmith.validator

class MessageCollector {
    private val messages = mutableListOf<IValidatorMessage>()
    fun report(message: IValidatorMessage) {
        messages.add(message)
    }

    fun hasMessage(): Boolean {
        return messages.isNotEmpty()
    }

    fun throwOnErrors() {
        if (hasMessage()) {
            throw IrValidateException(messages.joinToString("\n") { it.toString() })
        }
    }
}