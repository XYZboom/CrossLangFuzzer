

// This file was generated automatically. See README.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "unused")

package com.github.xyzboom.codesmith.ir.declarations.builder

import com.github.xyzboom.codesmith.ir.IrParameterList
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.builder.BuilderDsl
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.impl.IrFunctionDeclarationImpl
import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrUnit
import kotlin.contracts.*

@BuilderDsl
class IrFunctionDeclarationBuilder {
    lateinit var name: String
    var language: Language = Language.KOTLIN
    val typeParameters: MutableList<IrTypeParameter> = mutableListOf()
    var printNullableAnnotations: Boolean = false
    var body: IrBlock? = null
    var isOverride: Boolean = false
    var isOverrideStub: Boolean = false
    val override: MutableList<IrFunctionDeclaration> = mutableListOf()
    var isFinal: Boolean = false
    lateinit var parameterList: IrParameterList
    var returnType: IrType = IrUnit
    var containingClassName: String? = null

    fun build(): IrFunctionDeclaration {
        return IrFunctionDeclarationImpl(
            name,
            language,
            typeParameters,
            printNullableAnnotations,
            body,
            isOverride,
            isOverrideStub,
            override,
            isFinal,
            parameterList,
            returnType,
            containingClassName,
        )
    }

}

@OptIn(ExperimentalContracts::class)
inline fun buildFunctionDeclaration(init: IrFunctionDeclarationBuilder.() -> Unit): IrFunctionDeclaration {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }
    return IrFunctionDeclarationBuilder().apply(init).build()
}
