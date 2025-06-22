package io.github.xyzboom.crosslangfuzzer.tree.generator.model

import org.jetbrains.kotlin.generators.tree.*
import org.jetbrains.kotlin.generators.tree.ListField as AbstractListField

sealed class Field : AbstractField<Field>() {
    override var defaultValueInBuilder: String? = null

    override var customSetter: String? = null

    abstract override var isVolatile: Boolean

    abstract override var isFinal: Boolean

    abstract override var isParameter: Boolean

    abstract override var isMutable: Boolean

    override fun updateFieldsInCopy(copy: Field) {
        super.updateFieldsInCopy(copy)
        copy.customInitializationCall = customInitializationCall
        copy.skippedInCopy = skippedInCopy
    }
}

class SimpleField(
    override val name: String,
    override var typeRef: TypeRefWithNullability,
    override val isChild: Boolean,
    override var isMutable: Boolean,
    override var isVolatile: Boolean = false,
    override var isFinal: Boolean = false,
    override var isParameter: Boolean = false,
) : Field() {

    override fun internalCopy(): Field {
        return SimpleField(
            name = name,
            typeRef = typeRef,
            isChild = isChild,
            isMutable = isMutable,
            isVolatile = isVolatile,
            isFinal = isFinal,
            isParameter = isParameter,
        )
    }

    override fun substituteType(map: TypeParameterSubstitutionMap) {
        typeRef = typeRef.substitute(map) as TypeRefWithNullability
    }
}
// ----------- Field list -----------

class ListField(
    override val name: String,
    override var baseType: TypeRef,
    override val isChild: Boolean,
    val isMutableOrEmptyList: Boolean = false,
) : Field(), AbstractListField {
    override val typeRef: ClassRef<PositionTypeParameterRef>
        get() = super.typeRef

    override val listType: ClassRef<PositionTypeParameterRef>
        get() = StandardTypes.list

    override var isVolatile: Boolean = false
    override var isFinal: Boolean = false
    override var isMutable: Boolean = true
    override var isParameter: Boolean = false

    override fun internalCopy(): Field {
        return ListField(
            name,
            baseType,
            isChild,
            isMutableOrEmptyList
        )
    }

    override fun substituteType(map: TypeParameterSubstitutionMap) {
        baseType = baseType.substitute(map)
    }
}
