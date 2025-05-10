package com.github.xyzboom.codesmith.generator

import io.github.xyzboom.bf.tree.INode
import io.github.xyzboom.bf.tree.IRef
import io.github.xyzboom.bf.tree.RefNode
import com.github.xyzboom.codesmith.bf.generated.*
import com.github.xyzboom.codesmith.newir.ClassKind
import com.github.xyzboom.codesmith.newir.IrProgram
import com.github.xyzboom.codesmith.newir.decl.DeclName
import com.github.xyzboom.codesmith.newir.decl.IrClassDeclaration

class BfGenerator(
    private val config: GeneratorConfig = GeneratorConfig.default,
) : CrossLangFuzzerDefGenerator() {
    fun generate(): IrProgram {
        clearGeneratedNodes()
        // TODO set major language
        return generateProg() as IrProgram
    }

    private val generatedNames = mutableSetOf<String>().apply {
        addAll(KeyWords.java)
        addAll(KeyWords.kotlin)
        addAll(KeyWords.scala)
        addAll(KeyWords.builtins)
        addAll(KeyWords.windows)
    }

    /**
     * This method returns a name started with a lowercase char.
     * The printer will take responsibility of uppercasing the first char.
     */
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

    override fun chooseTopDeclReference(parent: IParentOfTopDecl): IRef? {
        return null
    }

    override fun chooseLangReference(): IRef? {
        return null
    }

    override fun choose_topDeclReference(parent: IParentOf_topDecl): IRef? {
        return null
    }

    override fun chooseClassReference(parent: IParentOfClass): IRef? {
        return when (parent) {
            is ISuperTypeNode -> when (val parent2 = parent.parent as IParentOfSuperType) {
                is IClassNode, is ISuperIntfListNode -> {
                    TODO()
                }

                is ITypeNode -> {
                    return RefNode(generatedClassNodes.random(random))
                }

                else -> throw NoWhenBranchMatchedException()
            }

            is I_topDeclNode -> null
            else -> throw NoWhenBranchMatchedException()
        }
    }

    override fun chooseClassKindReference(): IRef? {
        return null
    }

    override fun chooseSuperIntfListReference(parent: IParentOfSuperIntfList): IRef? {
        return null
    }

    override fun chooseMemberDeclReference(parent: IParentOfMemberDecl): IRef? {
        return null
    }

    override fun chooseMemberMethodReference(parent: IParentOfMemberMethod): IRef? {
        return when (parent) {
            is IMemberDeclNode -> null
            is IOverrideNode -> TODO()
            else -> throw NoWhenBranchMatchedException()
        }
    }

    override fun chooseOverrideReference(parent: IParentOfOverride): IRef? {
        return null
    }

    override fun chooseParamReference(parent: IParentOfParam): IRef? {
        return null
    }

    override fun chooseTypeReference(parent: IParentOfType): IRef? {
        return null
    }

    override fun chooseSuperTypeReference(parent: IParentOfSuperType): IRef? {
        return null
    }

    override fun chooseDeclNameReference(): IRef? {
        return null
    }

    override fun chooseTypeParamReference(parent: IParentOfTypeParam): IRef? {
        return when (parent) {
            is IClassNode -> null
            is ITypeNode -> TODO()
            else -> throw NoWhenBranchMatchedException()
        }
    }

    override fun chooseTypeArgReference(parent: IParentOfTypeArg): IRef? {
        return when (parent) {
            is ISuperTypeNode -> null
            else -> throw NoWhenBranchMatchedException()
        }
    }

    override fun chooseFieldReference(parent: IParentOfField): IRef? {
        return null // todo
    }

    override fun chooseFuncReference(parent: IParentOfFunc): IRef? {
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
    override fun newProg(): IProgNode {
        return IrProgram()
    }

    override fun newClass(): IClassNode {
        return IrClassDeclaration()
    }

    //</editor-fold>

    //<editor-fold desc="choose index">
    override fun choose_topDeclIndex(context: IParentOf_topDecl): Int {
        return 0 // currently toplevel class only
    }

    override fun chooseTypeIndex(context: IParentOfType): Int {
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