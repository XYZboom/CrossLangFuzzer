package com.github.xyzboom.codesmith.serde

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.github.xyzboom.codesmith.ir_old.expressions.IrExpression
import com.github.xyzboom.codesmith.ir_old.types.IrType
import org.reflections.Reflections


private val reflections: Reflections = Reflections("com.github.xyzboom.codesmith")

val defaultIrMapper: ObjectMapper = jsonMapper {
    addModule(kotlinModule {
        enable(KotlinFeature.SingletonSupport)
    })
    val irTypeClasses = reflections.getSubTypesOf(IrType::class.java)
    for (irTypeClass in irTypeClasses) {
        registerSubtypes(irTypeClass)
    }
    val irExpressionClasses = reflections.getSubTypesOf(IrExpression::class.java)
    for (irExpressionClass in irExpressionClasses) {
        registerSubtypes(irExpressionClass)
    }
}.setDefaultPropertyInclusion(JsonInclude.Include.NON_DEFAULT)