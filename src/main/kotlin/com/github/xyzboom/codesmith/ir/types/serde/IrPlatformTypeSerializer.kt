package com.github.xyzboom.codesmith.ir.types.serde

import com.github.xyzboom.codesmith.ir.serde.SERDE_TYPE_NAME
import com.github.xyzboom.codesmith.ir.types.IrPlatformType
import com.github.xyzboom.codesmith.ir.types.IrType
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

object IrPlatformTypeSerializer : JsonSerializer<IrPlatformType> {
    override fun serialize(
        src: IrPlatformType?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        if (src == null) return JsonNull.INSTANCE
        val result = JsonObject().apply {
            addProperty(SERDE_TYPE_NAME, IrPlatformType::class.simpleName)
            add(src::innerType.name, context?.serialize(src.innerType, IrType::class.java))
        }
        return result
    }

}