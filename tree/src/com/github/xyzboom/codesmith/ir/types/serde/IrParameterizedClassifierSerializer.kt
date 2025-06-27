package com.github.xyzboom.codesmith.ir.types.serde

import com.github.xyzboom.codesmith.ir.serde.SERDE_TYPE_NAME
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

object IrParameterizedClassifierSerializer: JsonSerializer<IrParameterizedClassifier> {
    override fun serialize(
        src: IrParameterizedClassifier?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement? {
        if (src == null) return JsonNull.INSTANCE
        return JsonObject().apply {
            addProperty(SERDE_TYPE_NAME, IrParameterizedClassifier::class.simpleName)
            addProperty(src::classDecl.name, src.classDecl.name)
            if (src.arguments.isNotEmpty()) {
                val argsObj = JsonObject()
                for ((typeParamName, pair) in src.arguments) {
                    val array = JsonArray()
                    array.add(context?.serialize(pair.first, IrTypeParameter::class.java))
                    array.add(context?.serialize(pair.second, IrType::class.java))
                    argsObj.add(typeParamName.value, array)
                }
                add(src::arguments.name, argsObj)
            }
        }
    }
}