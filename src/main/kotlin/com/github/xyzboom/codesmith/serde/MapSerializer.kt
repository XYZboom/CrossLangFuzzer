package com.github.xyzboom.codesmith.serde

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider

class MapSerializer : JsonSerializer<Map<*, *>>() {
    override fun serialize(value: Map<*, *>, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()
        gen.writeEndObject()
    }

}