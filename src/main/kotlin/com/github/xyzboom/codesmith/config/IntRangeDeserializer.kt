package com.github.xyzboom.codesmith.config

import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import java.lang.reflect.Type

object IntRangeDeserializer : JsonDeserializer<IntRange> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): IntRange? {
        json ?: return null
        if (json == JsonNull.INSTANCE) return null
        json as JsonArray
        val min = json.get(0).asInt
        val max = json.get(1).asInt
        return min..max
    }
}