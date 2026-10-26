package com.github.xyzboom.codesmith.ir

/** Source-language visibility; values intentionally preserve language-specific semantics. */
enum class Visibility {
    PUBLIC,
    PROTECTED,
    PACKAGE_PRIVATE,
    INTERNAL,
    PRIVATE,
}
