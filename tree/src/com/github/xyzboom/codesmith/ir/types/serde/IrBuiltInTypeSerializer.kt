package com.github.xyzboom.codesmith.ir.types.serde

import com.github.xyzboom.codesmith.ir.serde.IR_TYPE_NAME_FOR_BUILTIN
import com.github.xyzboom.codesmith.ir.serde.SERDE_TYPE_NAME
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

object IrBuiltInTypeSerializer : JsonSerializer<IrBuiltInType> {
    override fun serialize(
        src: IrBuiltInType?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement? {
        if (src == null) return JsonNull.INSTANCE
        return JsonObject().apply {
            addProperty(SERDE_TYPE_NAME, IrBuiltInType::class.simpleName)
            addProperty(IR_TYPE_NAME_FOR_BUILTIN, src::class.simpleName!!)
        }
    }

}