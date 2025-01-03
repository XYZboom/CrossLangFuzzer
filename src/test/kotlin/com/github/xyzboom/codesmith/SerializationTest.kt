package com.github.xyzboom.codesmith

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.expressions.IrBlock
import com.github.xyzboom.codesmith.ir.types.IrClassType
import com.github.xyzboom.codesmith.serde.defaultIrMapper
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SerializationTest {

    @Test
    fun testSerializeSimpleClassWithSimpleFunction() {
        val clazzName = "SimpleClassWithSimpleFunction"
        val funcName = "simple"
        val clazz = IrClassDeclaration(clazzName, IrClassType.FINAL)
        val func = IrFunctionDeclaration(funcName, clazz).apply {
            isFinal = true
            body = IrBlock()
            returnType = clazz.type
        }
        clazz.functions.add(func)
        val serialized = defaultIrMapper.writeValueAsString(clazz)
        val str = "{\n" +
                "  \"@type\" : \"class\",\n" +
                "  \"@id\" : 1,\n" +
                "  \"name\" : \"SimpleClassWithSimpleFunction\",\n" +
                "  \"classType\" : \"FINAL\",\n" +
                "  \"functions\" : [ {\n" +
                "    \"@type\" : \"function\",\n" +
                "    \"@id\" : 2,\n" +
                "    \"name\" : \"simple\",\n" +
                "    \"container\" : 1,\n" +
                "    \"body\" : {\n" +
                "      \"@type\" : \"IrBlock\",\n" +
                "      \"@id\" : 3\n" +
                "    },\n" +
                "    \"isFinal\" : true\n" +
//                "    \"parameterList\" : {\n" +
//                "      \"@type\" : \"IrParameterList\",\n" +
//                "      \"@id\" : 4\n" +
//                "    },\n" +
//                "    \"returnType\" : {\n" +
//                "      \"@type\" : \"IrUnit\",\n" +
//                "      \"@id\" : 5\n" +
//                "    }\n" +
                "  } ]\n" +
                "}"
        val deserialized = defaultIrMapper.readValue<IrClassDeclaration>(serialized)
        assertEquals(1, deserialized.functions.size)
        assertEquals(deserialized, deserialized.functions.single().container)
    }
}