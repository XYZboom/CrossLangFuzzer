package com.github.xyzboom.codesmith.data

import kotlin.math.max

data class ProgramData(
    var classCount: Int = 0,
    var methodCount: Int = 0,
    var maxInheritanceDepth: Int = 0,
    var maxInheritanceWidth: Int = 0,
    var avgInheritanceDepth: Float = 0f,
    var avgInheritanceWidth: Float = 0f,
    var lineOfCode: Int = 0
) {
    operator fun plus(other: ProgramData): ProgramData {
        return ProgramData(
            classCount = classCount + other.classCount,
            methodCount = methodCount + other.methodCount,
            maxInheritanceDepth = max(maxInheritanceDepth, other.maxInheritanceDepth),
            maxInheritanceWidth = max(maxInheritanceWidth, other.maxInheritanceWidth),
            avgInheritanceDepth = (avgInheritanceDepth * classCount
                    + other.avgInheritanceDepth * other.classCount) / (classCount + other.classCount),
            avgInheritanceWidth = (avgInheritanceWidth * classCount
                    + other.avgInheritanceWidth * other.classCount) / (classCount + other.classCount),
            lineOfCode = lineOfCode + other.lineOfCode
        )
    }

    operator fun plusAssign(other: ProgramData) {
        classCount += other.classCount
        methodCount += other.methodCount
        maxInheritanceDepth = max(maxInheritanceDepth, other.maxInheritanceDepth)
        maxInheritanceWidth = max(maxInheritanceWidth, other.maxInheritanceWidth)
        avgInheritanceDepth = (avgInheritanceDepth * classCount
                + other.avgInheritanceDepth * other.classCount) / (classCount + other.classCount)
        avgInheritanceWidth = (avgInheritanceWidth * classCount
                + other.avgInheritanceWidth * other.classCount) / (classCount + other.classCount)
        lineOfCode += other.lineOfCode
    }
}
