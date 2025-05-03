package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.bf.tree.INode
import com.github.xyzboom.bf.tree.IRef
import com.github.xyzboom.bf.tree.RefNode
import com.github.xyzboom.codesmith.bf.generated.*
import com.github.xyzboom.codesmith.newir.ClassKind
import com.github.xyzboom.codesmith.newir.decl.DeclName

class BfGenerator(
    private val config: GeneratorConfig = GeneratorConfig.default,
) : CrossLangFuzzerDefGenerator() {
    fun generate(): DefaultProgNode {
        clearGeneratedNodes()
        return generateProg() as DefaultProgNode
    }

    private val generatedNames = mutableSetOf<String>().apply {
        addAll(KeyWords.java)
        addAll(KeyWords.kotlin)
        addAll(KeyWords.scala)
        addAll(KeyWords.builtins)
        addAll(KeyWords.windows)
    }

    fun randomName(): String {
        val length = config.nameLengthRange.random(random)
        val sb = StringBuilder(
            "${lowerStartingLetters.random(random)}"
        )
        repeat(length - 1) {
            sb.append(lettersAndNumbers.random(random))
        }
        val result = sb.toString()
        val lowercase = result.lowercase()
        if (generatedNames.contains(lowercase)) {
            return randomName()
        }
        generatedNames.add(lowercase)
        return result
    }

    //<editor-fold desc="choose reference">
    override fun chooseProgReference(): IRef? {
        return null
    }

    override fun chooseTopDeclReference(parent: ITopDeclParent): IRef? {
        return null
    }

    override fun chooseLangReference(): IRef? {
        return null
    }

    override fun choose_topDeclReference(parent: I_topDeclParent): IRef? {
        return null
    }

    override fun chooseClassReference(parent: IClassParent): IRef? {
        return when (parent) {
            is ISuperTypeNode -> when (val parent2 = parent.parent as ISuperTypeParent) {
                is IClassNode, is ISuperIntfListNode -> {
                    TODO()
                }

                is ITypeNode -> {
                    return RefNode(generatedClassNodes.random(random))
                }
            }

            is I_topDeclNode -> null
        }
    }

    override fun chooseClassKindReference(): IRef? {
        return null
    }

    override fun chooseSuperIntfListReference(parent: ISuperIntfListParent): IRef? {
        return null
    }

    override fun chooseMemberDeclReference(parent: IMemberDeclParent): IRef? {
        return null
    }

    override fun chooseMemberMethodReference(parent: IMemberMethodParent): IRef? {
        return when (parent) {
            is IMemberDeclNode -> null
            is IOverrideNode -> TODO()
        }
    }

    override fun chooseReturnTypeReference(parent: IReturnTypeParent): IRef? {
        return null
    }

    override fun chooseOverrideReference(parent: IOverrideParent): IRef? {
        return null
    }

    override fun chooseParamReference(parent: IParamParent): IRef? {
        return null
    }

    override fun chooseTypeReference(parent: ITypeParent): IRef? {
        return null
    }

    override fun chooseSuperTypeReference(parent: ISuperTypeParent): IRef? {
        return null
    }

    override fun chooseDeclNameReference(): IRef? {
        return null
    }

    override fun chooseTypeParamReference(parent: ITypeParamParent): IRef? {
        return when (parent) {
            is IClassNode -> null
            is ITypeNode -> TODO()
        }
    }

    override fun chooseTypeArgReference(parent: ITypeArgParent): IRef? {
        return when (parent) {
            is ISuperTypeNode -> null
        }
    }

    override fun chooseFieldReference(parent: IFieldParent): IRef? {
        return null // todo
    }

    override fun chooseFuncReference(parent: IFuncParent): IRef? {
        return null // todo
    }
    //</editor-fold>

    //<editor-fold desc="choose size">
    override fun chooseTopDeclSizeWhenParentIsProg(parent: IProgNode): Int {
        return super.chooseTopDeclSizeWhenParentIsProg(parent)
    }

    override fun chooseSuperTypeSizeWhenParentIsClass(parent: IClassNode): Int {
        // TODO make super type available
        return 0
    }

    override fun chooseMemberDeclSizeWhenParentIsClass(parent: IClassNode): Int {
        return super.chooseMemberDeclSizeWhenParentIsClass(parent)
    }

    override fun chooseSuperTypeSizeWhenParentIsSuperIntfList(parent: ISuperIntfListNode): Int {
        // TODO make super interface avaliable
        return 0
    }

    override fun chooseParamSizeWhenParentIsMemberMethod(parent: IMemberMethodNode): Int {
        return super.chooseParamSizeWhenParentIsMemberMethod(parent)
    }

    override fun chooseOverrideSizeWhenParentIsMemberMethod(parent: IMemberMethodNode): Int {
        // TODO redesign override here
        return 0
    }

    override fun chooseTypeArgSizeWhenParentIsSuperType(parent: ISuperTypeNode): Int {
        return super.chooseTypeArgSizeWhenParentIsSuperType(parent)
    }

    //</editor-fold>

    //<editor-fold desc="new node functions">

    //</editor-fold>

    //<editor-fold desc="choose index">
    override fun choose_topDeclIndex(context: I_topDeclParent): Int {
        return 0 // currently toplevel class only
    }

    override fun chooseTypeIndex(context: ITypeParent): Int {
        // TODO consider a type parameter
        return 1
    }

    //</editor-fold>
    //<editor-fold desc="generate functions">
    override fun generateDeclName(): INode {
        return DeclName(randomName())
    }

    override fun generateClassKind(): INode {
        return ClassKind.entries.random(random)
    }
    //</editor-fold>
}