package com.github.xyzboom.codesmith.ir.serde

import com.github.xyzboom.codesmith.ir.ClassKind
import com.github.xyzboom.codesmith.ir.IrProgram
import com.github.xyzboom.codesmith.ir.Language
import com.github.xyzboom.codesmith.ir.builder.buildParameterList
import com.github.xyzboom.codesmith.ir.builder.buildProgram
import com.github.xyzboom.codesmith.ir.declarations.IrClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.IrFunctionDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildClassDeclaration
import com.github.xyzboom.codesmith.ir.declarations.builder.buildFunctionDeclaration
import com.github.xyzboom.codesmith.ir.expressions.builder.buildBlock
import com.github.xyzboom.codesmith.ir.types.IrNullableType
import com.github.xyzboom.codesmith.ir.types.IrParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.IrSimpleClassifier
import com.github.xyzboom.codesmith.ir.types.IrType
import com.github.xyzboom.codesmith.ir.types.IrTypeParameter
import com.github.xyzboom.codesmith.ir.types.IrTypeParameterName
import com.github.xyzboom.codesmith.ir.types.builder.buildNullableType
import com.github.xyzboom.codesmith.ir.types.builder.buildParameterizedClassifier
import com.github.xyzboom.codesmith.ir.types.builder.buildSimpleClassifier
import com.github.xyzboom.codesmith.ir.types.builder.buildTypeParameter
import com.github.xyzboom.codesmith.ir.types.builtin.IrAny
import com.github.xyzboom.codesmith.ir.types.builtin.IrBuiltInType
import com.github.xyzboom.codesmith.ir.types.builtin.IrNothing
import com.github.xyzboom.codesmith.ir.types.builtin.IrUnit
import com.github.xyzboom.codesmith.ir.containers.IrTypeParameterContainer
import com.github.xyzboom.codesmith.ir.declarations.builder.buildParameter
import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import java.lang.reflect.Type

object IrProgramDeserializer : JsonDeserializer<IrProgram> {

    private class IrProgramDeserializerPhase(private val context: JsonDeserializationContext) {

        private val classMap = mutableMapOf<String, IrClassDeclaration>()

        fun deserializeSimpleClassifier(typeObj: JsonObject): IrSimpleClassifier {
            require(typeObj.get(SERDE_TYPE_NAME).asString == IrSimpleClassifier::class.simpleName)
            return buildSimpleClassifier {
                val className = typeObj.get(IrSimpleClassifier::classDecl.name).asString
                classDecl = classMap[className] ?: throw JsonParseException("No class named $className")
            }
        }

        fun deserializeParameterizedClassifier(
            typeObj: JsonObject,
            context: JsonDeserializationContext
        ): IrParameterizedClassifier {
            require(typeObj.get(SERDE_TYPE_NAME).asString == IrParameterizedClassifier::class.simpleName)
            return buildParameterizedClassifier {
                val className = typeObj.get(IrParameterizedClassifier::classDecl.name).asString
                classDecl = classMap[className] ?: throw JsonParseException("No class named $className")
                arguments = mutableMapOf()
                val argsObj = typeObj.get(IrParameterizedClassifier::arguments.name)
                if (argsObj != null && argsObj is JsonObject) {
                    for ((name: String, argPairObj) in argsObj.asMap()) {
                        argPairObj as JsonArray
                        val paramObj = argPairObj[0]
                        val argObj = argPairObj[1]
                        val arg: IrType? = argObj?.let { deserializeType(it.asJsonObject, context) }
                        val param = deserializeTypeParameter(paramObj.asJsonObject, context)
                        arguments[IrTypeParameterName(name)] = param to arg
                    }
                }
            }
        }

        fun deserializeBuiltinType(typeObj: JsonObject): IrBuiltInType {
            require(typeObj.get(SERDE_TYPE_NAME).asString == IrBuiltInType::class.simpleName)
            return when (val name = typeObj.get(IR_TYPE_NAME_FOR_BUILTIN).asString) {
                IrAny::class.simpleName -> IrAny
                IrUnit::class.simpleName -> IrUnit
                IrNothing::class.simpleName -> IrNothing
                else -> throw NoWhenBranchMatchedException("No such builtin type: $name")
            }
        }

        fun deserializeNullableType(
            typeObj: JsonObject,
            context: JsonDeserializationContext
        ): IrNullableType {
            require(typeObj.get(SERDE_TYPE_NAME).asString == IrNullableType::class.simpleName)
            return buildNullableType {
                innerType = deserializeType(typeObj.get(IrNullableType::innerType.name).asJsonObject, context)
            }
        }

        fun deserializeTypeParameter(
            typeParamObj: JsonObject,
            context: JsonDeserializationContext
        ): IrTypeParameter {
            require(typeParamObj.get(SERDE_TYPE_NAME).asString == IrTypeParameter::class.simpleName)
            val typeParam = buildTypeParameter {
                name = typeParamObj.get(IrTypeParameter::name.name).asString
                val upperboundObj = typeParamObj.get(IrTypeParameter::upperbound.name)
                upperbound = if (upperboundObj != null) {
                    deserializeType(upperboundObj.asJsonObject, context)
                } else {
                    IrAny
                }
            }
            return typeParam
        }

        fun deserializeType(
            typeObj: JsonObject,
            context: JsonDeserializationContext
        ): IrType {
            val typeName: String = typeObj.get(SERDE_TYPE_NAME).asString
            return when (typeName) {
                IrSimpleClassifier::class.simpleName -> deserializeSimpleClassifier(typeObj)
                IrParameterizedClassifier::class.simpleName -> deserializeParameterizedClassifier(typeObj, context)
                IrNullableType::class.simpleName -> deserializeNullableType(typeObj, context)
                IrBuiltInType::class.simpleName -> deserializeBuiltinType(typeObj)
                IrTypeParameter::class.simpleName -> deserializeTypeParameter(typeObj, context)
                else -> throw NoWhenBranchMatchedException("No such type: $typeName")
            }
        }

        fun IrTypeParameterContainer.deserializeTypeParameters(
            containerObjMap: Map<String, JsonElement>,
            context: JsonDeserializationContext
        ) {
            with(containerObjMap) {
                val typeParamsObj = containerObjMap[::typeParameters.name]
                if (typeParamsObj != null && typeParamsObj is JsonArray) {
                    for (typeParamObj in typeParamsObj) {
                        typeParamObj as JsonObject
                        val typeParam = deserializeTypeParameter(typeParamObj, context)
                        typeParameters.add(typeParam)
                    }
                }
            }
        }

        fun deserializeFunctionFirstStage(
            funcObj: JsonObject,
            context: JsonDeserializationContext,
            classContext: IrClassDeclaration?
        ): IrFunctionDeclaration {
            return buildFunctionDeclaration {
                name = funcObj.get(::name.name).asString
                val langObj = funcObj.get(::language.name)
                if (langObj == null) {
                    if (classContext != null) {
                        language = classContext.language
                    } else {
                        throw JsonParseException("Function $name in toplevel should have language tag")
                    }
                } else {
                    language = context.deserialize(langObj, Language::class.java)
                }
                printNullableAnnotations = funcObj.get(::printNullableAnnotations.name)?.asBoolean ?: false
                isOverride = funcObj.get(::isOverride.name)?.asBoolean ?: false
                isOverrideStub = funcObj.get(::isOverrideStub.name)?.asBoolean ?: false
                isFinal = funcObj.get(::isFinal.name)?.asBoolean ?: false
                parameterList = buildParameterList()
                // just a placeholder, we will do this in the second stage
                returnType = IrAny
            }
        }

        fun deserializeFunctionForClassSecondStage(
            // key: funcName, value: funcObj
            // The serialized json string is an array of functions.
            // Here we need search the function by its name
            funcObjMap: Map<String, JsonElement>,
            context: JsonDeserializationContext,
            classContext: IrClassDeclaration
        ) {
            val firstFuncMap = classContext.functions.associateBy { it.name }
            for ((name, funcObj) in funcObjMap) {
                funcObj as JsonObject
                val func = firstFuncMap[name]!!
                with(func) {
                    deserializeTypeParameters(funcObj.asMap(), context)
                    val bodyObj = funcObj.get(::body.name)
                    if (bodyObj != null) {
                        // currently, we do not have a body,
                        // but in the future, the body deserialization need to be in second stage
                        body = buildBlock()
                    }
                    val overrideObj = funcObj.get(::override.name)
                    if (overrideObj != null && overrideObj is JsonArray) {
                        for (o in overrideObj) {
                            val overriddenClass = classMap[o.asString]!!
                            override.add(overriddenClass.functions.first { it.name == name })
                        }
                    }
                    val paramsObj = funcObj.get(::parameterList.name)
                    if (paramsObj != null && paramsObj is JsonArray) {
                        for (paramObj in paramsObj) {
                            paramObj as JsonObject
                            parameterList.parameters.add(buildParameter {
                                this.name = paramObj.get(::name.name).asString
                                this.type = deserializeType(paramObj.get(::type.name).asJsonObject, context)
                            })
                        }
                    }
                    returnType = deserializeType(funcObj.get(::returnType.name).asJsonObject, context)
                }
            }
        }

        /**
         * First, we collect all classes without content ready in json.
         */
        fun deserializeClassFirstStage(
            classObj: JsonObject,
            context: JsonDeserializationContext
        ): IrClassDeclaration {
            val clazz = buildClassDeclaration {
                name = classObj.get(::name.name).asString
                language = context.deserialize(classObj.get(::language.name), Language::class.java)
                classKind = context.deserialize(classObj.get(::classKind.name), ClassKind::class.java)
                allSuperTypeArguments = mutableMapOf()
            }
            val functionsObj = classObj.get(IrClassDeclaration::functions.name).asJsonArray
            for (funcObj in functionsObj) {
                clazz.functions.add(deserializeFunctionFirstStage(funcObj.asJsonObject, context, clazz))
            }
            classMap[clazz.name] = clazz
            return clazz
        }

        /**
         * Second, we fill the content of the classes.
         * We can get a class by its name from [classMap] filled from [deserializeClassFirstStage].
         */
        fun deserializeClassSecondStage(
            classObj: JsonObject,
            context: JsonDeserializationContext
        ): IrClassDeclaration {
            val name = classObj.get(IrClassDeclaration::name.name).asString
            val clazz = classMap[name]!!
            val functionsObj = classObj.get(IrClassDeclaration::functions.name).asJsonArray
            val functionsObjMap = functionsObj.asJsonArray.associateBy {
                it.asJsonObject.get(IrFunctionDeclaration::name.name).asString
            }
            deserializeFunctionForClassSecondStage(functionsObjMap, context, clazz)
            with(clazz) {
                deserializeTypeParameters(classObj.asMap(), context)
                val superTypeObj = classObj.get(IrClassDeclaration::superType.name)
                if (superTypeObj != null) {
                    superType = deserializeType(superTypeObj.asJsonObject, context)
                }
                val typeArgsObj = classObj.get(IrClassDeclaration::allSuperTypeArguments.name)
                if (typeArgsObj != null) {
                    for ((typeParamName, pairObj) in typeArgsObj.asJsonObject.asMap()) {
                        pairObj as JsonArray
                        val typeParamObj = pairObj[0].asJsonObject
                        val typeArgObj = pairObj[1].asJsonObject
                        val typeParam = deserializeTypeParameter(typeParamObj, context)
                        val typeArg = deserializeType(typeArgObj, context)
                        allSuperTypeArguments[IrTypeParameterName(typeParamName)] = typeParam to typeArg
                    }
                }
                val implsObj = classObj.get(IrClassDeclaration::implementedTypes.name)
                if (implsObj != null && implsObj is JsonArray) {
                    for (implObj in implsObj) {
                        implementedTypes.add(deserializeType(implObj.asJsonObject, context))
                    }
                }
            }
            return clazz
        }

        fun deserialize(map: Map<String, JsonElement>): IrProgram {
            return buildProgram {
                val classesObj = map[IrProgram::classes.name]
                if (classesObj == null) {
                    return@buildProgram
                }
                if (!classesObj.isJsonArray) {
                    throw JsonParseException("The classes of an ${IrProgram::class.simpleName} must be json array!")
                }
                for (classObj in classesObj.asJsonArray) {
                    deserializeClassFirstStage(classObj.asJsonObject, context)
                }
                for (classObj in classesObj.asJsonArray) {
                    val clazz = deserializeClassSecondStage(classObj.asJsonObject, context)
                    classes.add(clazz)
                }
            }
        }
    }

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type?,
        context: JsonDeserializationContext
    ): IrProgram {
        val jsonObject = json.asJsonObject
        val map: Map<String, JsonElement> = jsonObject.asMap()
        if (map[SERDE_TYPE_NAME]?.asString != IrProgram::class.simpleName) {
            throw JsonParseException("This element is not an ${IrProgram::class.simpleName}")
        }
        return IrProgramDeserializerPhase(context).deserialize(map)
    }
}