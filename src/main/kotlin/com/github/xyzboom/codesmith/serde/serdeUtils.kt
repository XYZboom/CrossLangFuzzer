package com.github.xyzboom.codesmith.serde

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.github.xyzboom.codesmith.config.IntRangeDeserializer
import com.github.xyzboom.codesmith.config.IntRangeSerializer
import com.github.xyzboom.codesmith.ir_old.expressions.IrExpression
import org.reflections.Reflections
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.containers.IrTypeParameterContainer
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.serde.IrClassDeclarationSerializer
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrParameter
import com.github.xyzboom.codesmith.ir.declarations.serde.IrFunctionDeclarationSerializer
import com.github.xyzboom.codesmith.ir.declarations.serde.IrParameterSerializer
import com.github.xyzboom.codesmith.ir.impl.IrProgramImpl
import com.github.xyzboom.codesmith.ir.serde.IrProgramDeserializer
import com.github.xyzboom.codesmith.ir.serde.IrProgramSerializer
import com.github.xyzboom.codesmith.ir.types.IrNullableType
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrSimpleClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType
import com.github.xyzboom.codesmith.ir.types.serde.IrBuiltInTypeSerializer
import com.github.xyzboom.codesmith.ir.types.serde.IrNullableTypeSerializer
import com.github.xyzboom.codesmith.ir.types.serde.IrParameterizedClassifierSerializer
import com.github.xyzboom.codesmith.ir.types.serde.IrSimpleClassifierSerializer
import com.github.xyzboom.codesmith.ir.types.serde.IrTypeParameterSerializer
import com.github.xyzboom.codesmith.ir.types.serde.IrTypeSerializer
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext

private val reflections: Reflections = Reflections("com.github.xyzboom.codesmith")

val defaultIrMapper: ObjectMapper by lazy {
    jsonMapper {
        addModule(kotlinModule {
            enable(KotlinFeature.SingletonSupport)
        })
        val irTypeClasses = reflections.getSubTypesOf(com.github.xyzboom.codesmith.ir_old.types.IrType::class.java)
        for (irTypeClass in irTypeClasses) {
            registerSubtypes(irTypeClass)
        }
        val irExpressionClasses = reflections.getSubTypesOf(IrExpression::class.java)
        for (irExpressionClass in irExpressionClasses) {
            registerSubtypes(irExpressionClass)
        }
    }.setDefaultPropertyInclusion(JsonInclude.Include.NON_DEFAULT)
}


val gson: Gson = GsonBuilder()
    .registerTypeAdapter(IrProgramImpl::class.java, IrProgramSerializer)
    .registerTypeAdapter(IrProgram::class.java, IrProgramDeserializer)
    .registerTypeAdapter(IrClassDeclaration::class.java, IrClassDeclarationSerializer)
    .registerTypeAdapter(IrFunctionDeclaration::class.java, IrFunctionDeclarationSerializer)
    .registerTypeAdapter(IrParameter::class.java, IrParameterSerializer)
    .registerTypeAdapter(IrType::class.java, IrTypeSerializer)
    .registerTypeAdapter(IrSimpleClassifier::class.java, IrSimpleClassifierSerializer)
    .registerTypeAdapter(IrParameterizedClassifier::class.java, IrParameterizedClassifierSerializer)
    .registerTypeAdapter(IrNullableType::class.java, IrNullableTypeSerializer)
    .registerTypeAdapter(IrBuiltInType::class.java, IrBuiltInTypeSerializer)
    .registerTypeAdapter(IrTypeParameter::class.java, IrTypeParameterSerializer)
    .create()

val configGson: Gson = GsonBuilder()
    .registerTypeAdapter(IntRange::class.java, IntRangeSerializer)
    .registerTypeAdapter(IntRange::class.java, IntRangeDeserializer)
    .create()

fun JsonObject.addTypeParameters(typeParameterContainer: IrTypeParameterContainer, p2: JsonSerializationContext?) {
    with(typeParameterContainer) {
        if (typeParameters.isNotEmpty()) {
            val typeParamsObj = JsonArray()
            for (typeParam in typeParameters) {
                typeParamsObj.add(p2?.serialize(typeParam, IrTypeParameter::class.java))
            }
            add(::typeParameters.name, typeParamsObj)
        }
    }
}
