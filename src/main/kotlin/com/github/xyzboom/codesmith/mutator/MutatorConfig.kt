package com.github.xyzboom.codesmith.mutator

import kotlin.Int

data class MutatorConfig(
    val mutateGenericArgumentInParentWeight: Int = 1,
    val removeOverrideMemberFunctionWeight: Int = 1,
    val mutateGenericArgumentInMemberFunctionParameterWeight: Int = 3,
    val mutateParameterNullabilityWeight: Int = 2,
    val mutateReturnTypeNullabilityWeight: Int = 2,
    var mutateClassTypeParameterUpperBoundNullabilityWeight: Int = 10,
    var mutateClassTypeParameterUpperBoundWeight: Int = 10,
    var mutateInterfaceMethodDefaultWeight: Int = 10,
) {
    companion object {
        @JvmStatic
        val default = MutatorConfig()

        @JvmStatic
        val allZero = MutatorConfig(
            mutateGenericArgumentInParentWeight = 0,
            removeOverrideMemberFunctionWeight = 0,
            mutateGenericArgumentInMemberFunctionParameterWeight = 0,
            mutateParameterNullabilityWeight = 0,
            mutateReturnTypeNullabilityWeight = 0,
            mutateClassTypeParameterUpperBoundNullabilityWeight = 0,
            mutateClassTypeParameterUpperBoundWeight = 0,
            mutateInterfaceMethodDefaultWeight = 0
        )
    }
}