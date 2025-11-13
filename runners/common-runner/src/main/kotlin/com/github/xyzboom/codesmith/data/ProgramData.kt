package com.github.xyzboom.codesmith.data

import kotlin.math.max

data class ProgramData(
    var programCount: Int = 0,
    var classCount: Int = 0,
    var methodCount: Int = 0,
    var maxInheritanceDepth: Int = 0,
    var maxInheritanceWidth: Int = 0,
    // avg of max depth per program
    var avgInheritanceDepth: Float = 0f,
    var avgInheritanceWidth: Float = 0f,
    var typeParameterCount: Int = 0,
    var parameterCount: Int = 0,
    var lineOfCode: Int = 0
) {
    operator fun plus(other: ProgramData): ProgramData {
        return ProgramData(
            programCount = programCount + other.programCount,
            classCount = classCount + other.classCount,
            methodCount = methodCount + other.methodCount,
            maxInheritanceDepth = max(maxInheritanceDepth, other.maxInheritanceDepth),
            maxInheritanceWidth = max(maxInheritanceWidth, other.maxInheritanceWidth),
            avgInheritanceDepth = (avgInheritanceDepth * programCount
                    + other.avgInheritanceDepth * other.programCount) / (programCount + other.programCount),
            avgInheritanceWidth = (avgInheritanceWidth * programCount
                    + other.avgInheritanceWidth * other.programCount) / (programCount + other.programCount),
            typeParameterCount = typeParameterCount + other.typeParameterCount,
            parameterCount = parameterCount + other.parameterCount,
            lineOfCode = lineOfCode + other.lineOfCode
        )
    }

    operator fun plusAssign(other: ProgramData) {
        programCount += other.programCount
        classCount += other.classCount
        methodCount += other.methodCount
        maxInheritanceDepth = max(maxInheritanceDepth, other.maxInheritanceDepth)
        maxInheritanceWidth = max(maxInheritanceWidth, other.maxInheritanceWidth)
        avgInheritanceDepth = (avgInheritanceDepth * classCount
                + other.avgInheritanceDepth * other.classCount) / (classCount + other.classCount)
        avgInheritanceWidth = (avgInheritanceWidth * classCount
                + other.avgInheritanceWidth * other.classCount) / (classCount + other.classCount)
        typeParameterCount += other.typeParameterCount
        parameterCount += other.parameterCount
        lineOfCode += other.lineOfCode
    }
}
