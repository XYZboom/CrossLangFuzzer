package com.github.xyzboom.codesmith.tree.generator.model

import com.github.xyzboom.codesmith.tree.generator.BASE_PACKAGE
import org.jetbrains.kotlin.generators.tree.AbstractElement
import org.jetbrains.kotlin.generators.tree.ImplementationKind
import org.jetbrains.kotlin.generators.tree.safeDecapitalizedName

class Element(name: String, override val propertyName: String, kind: Kind) :
    AbstractElement<Element, Field, Implementation>(name) {
    override val namePrefix: String
        get() = "Ir"
    override val visitorParameterName: String
        get() = safeDecapitalizedName
    override val packageName: String = BASE_PACKAGE + kind.packageName.let { if (it.isNotBlank()) ".$it" else "" }

    companion object {
        private val allowedKinds = setOf(
            ImplementationKind.Interface,
            ImplementationKind.SealedInterface,
            ImplementationKind.AbstractClass,
            ImplementationKind.SealedClass
        )
    }

    override var kind: ImplementationKind?
        get() = super.kind
        set(value) {
            if (value !in allowedKinds) {
                throw IllegalArgumentException(value.toString())
            }
            super.kind = value
        }

    override val hasAcceptMethod: Boolean
        get() = true

    override val hasTransformMethod: Boolean
        get() = true

    var _needTransformOtherChildren: Boolean = false

    val needTransformOtherChildren: Boolean get() = _needTransformOtherChildren || elementParents.any { it.element.needTransformOtherChildren }

    enum class Kind(val packageName: String) {
        Expression("expressions"),
        Declaration("declarations"),
        Container("containers"),
        Type("types"),
        Other("")
    }
}