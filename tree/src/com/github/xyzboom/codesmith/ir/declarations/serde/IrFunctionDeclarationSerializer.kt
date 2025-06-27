package com.github.xyzboom.codesmith.ir.declarations.serde

import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrParameter
import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.serde.addTypeParameters
import com.github.xyzboom.codesmith.ir.types.IrType
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

object IrFunctionDeclarationSerializer : JsonSerializer<IrFunctionDeclaration> {
    override fun serialize(
        p0: IrFunctionDeclaration?,
        p1: Type?,
        p2: JsonSerializationContext?
    ): JsonElement {
        if (p0 == null) return JsonNull.INSTANCE
        val result = JsonObject().apply {
            with(p0) {
                addProperty(::name.name, name)
                if (containingClassName == null) {
                    add(::language.name, p2?.serialize(language))
                }
                addTypeParameters(this, p2)
                if (printNullableAnnotations) {
                    addProperty(::printNullableAnnotations.name, printNullableAnnotations)
                }
                if (body != null) {
                    add(::body.name, p2?.serialize(body, IrBlock::class.java))
                }
                if (isOverride) {
                    addProperty(::isOverride.name, isOverride)
                }
                if (isOverrideStub) {
                    addProperty(::isOverrideStub.name, isOverrideStub)
                }
                if (override.isNotEmpty()) {
                    val overrideObj = JsonArray()
                    for (o in override) {
                        overrideObj.add(o.containingClassName!!)
                    }
                    add(::override.name, overrideObj)
                }
                if (isFinal) {
                    addProperty(::isFinal.name, isFinal)
                }
                if (parameterList.parameters.isNotEmpty()) {
                    val parameterListObj = JsonArray()
                    for (param in parameterList.parameters) {
                        parameterListObj.add(p2?.serialize(param, IrParameter::class.java))
                    }
                    add(::parameterList.name, parameterListObj)
                }
                add(::returnType.name, p2?.serialize(returnType, IrType::class.java))
                // no need for containingClassName
            }
        }
        return result
    }
}