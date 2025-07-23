package com.github.xyzboom.codesmith.config

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

object IntRangeSerializer : JsonSerializer<IntRange> {
    override fun serialize(
        p0: IntRange?,
        p1: Type?,
        p2: JsonSerializationContext?
    ): JsonElement {
        p0 ?: return JsonNull.INSTANCE
        return JsonArray().apply {
            add(p0.first)
            add(p0.last)
        }
    }

}