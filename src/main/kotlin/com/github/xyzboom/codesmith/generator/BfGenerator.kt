package com.github.xyzboom.codesmith.generator

import com.github.xyzboom.bf.tree.INode
import com.github.xyzboom.bf.tree.IRef
import com.github.xyzboom.codesmith.bf.generated.*
import com.github.xyzboom.codesmith.newir.ClassKind

class BfGenerator : CrossLangFuzzerDefGenerator() {
    fun generate(): DefaultProgNode {
        clearGeneratedNodes()
        return generateProg() as DefaultProgNode
    }

    //<editor-fold desc="choose reference">
    override fun chooseProgReference(): IRef? {
        return null
    }

    override fun chooseTopDeclReference(parent: ITopDeclParent): IRef? {
        return null
    }

    override fun chooseLangReference(parent: ILangParent): IRef? {
        return null
    }

    override fun choose_topDeclReference(parent: I_topDeclParent): IRef? {
        return null
    }

    override fun chooseClassReference(parent: IClassParent): IRef? {
        return when (parent) {
            is ISuperTypeNode -> TODO()
            is I_topDeclNode -> null
        }
    }

    override fun chooseClassKindReference(parent: IClassKindParent): IRef? {
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

    override fun chooseDeclNameReference(parent: IDeclNameParent): IRef? {
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

    //<editor-fold desc="generate functions">
    override fun generateClassKind(parent: IClassKindParent): INode {
        return ClassKind.entries.random(random)
    }
    //</editor-fold>
}