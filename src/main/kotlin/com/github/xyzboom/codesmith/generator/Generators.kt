package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.container.IrContainer
import com.github.xyzboom.codesmith.ir.declarations.IrClassMember
import com.github.xyzboom.codesmith.ir.declarations.IrDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.expressions.IrExpression
import com.github.xyzboom.codesmith.ir.expressions.IrExpressionContainer
import com.github.xyzboom.codesmith.ir.types.IrType

typealias IrExpressionGenerator = (
    block: IrExpressionContainer,
    functionContext: IrFunctionDeclaration,
    context: IrProgram,
    type: IrType,
    allowSubType: Boolean
) -> IrExpression

typealias IrTopLevelDeclGenerator = (
    program: IrProgram,
    language: Language
) -> IrDeclaration

typealias IrClassMemberGenerator = (
    classContainer: IrContainer,
    memberContainer: IrContainer,
    inAbstract: Boolean,
    inIntf: Boolean,
    type: IrType?,
    name: String,
    language: Language
) -> IrClassMember