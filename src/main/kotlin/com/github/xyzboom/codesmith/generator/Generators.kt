package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.codesmith.Language
import com.github.xyzboom.codesmith.ir_old.IrProgram
import com.github.xyzboom.codesmith.ir_old.container.IrContainer
import com.github.xyzboom.codesmith.ir_old.declarations.IrClassMember
import com.github.xyzboom.codesmith.ir_old.declarations.IrDeclaration
import com.github.xyzboom.codesmith.ir_old.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir_old.expressions.IrExpression
import com.github.xyzboom.codesmith.ir_old.expressions.IrExpressionContainer
import com.github.xyzboom.codesmith.ir_old.types.IrType

typealias IrExpressionGenerator = (
    block: IrExpressionContainer,
    functionContext: IrFunctionDeclaration,
    context: IrProgram,
    type: IrType?,
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