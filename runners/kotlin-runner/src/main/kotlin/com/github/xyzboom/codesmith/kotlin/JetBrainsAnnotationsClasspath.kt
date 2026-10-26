package com.github.xyzboom.codesmith.kotlin

import org.jetbrains.annotations.NotNull
import java.io.File
import java.lang.annotation.ElementType
import java.lang.annotation.Target

/** Returns the pinned JetBrains annotations artifact used by generated-source compiles. */
internal fun jetbrainsAnnotationsJar(): File {
    val annotationClass = NotNull::class.java
    val targets = annotationClass.getAnnotation(Target::class.java)?.value.orEmpty()
    require(ElementType.TYPE_USE in targets) {
        "Resolved org.jetbrains.annotations.NotNull does not support TYPE_USE"
    }

    val location = annotationClass.protectionDomain.codeSource?.location
        ?: error("Cannot locate the resolved org.jetbrains:annotations artifact")
    val jar = File(location.toURI())
    require(jar.isFile) { "Resolved JetBrains annotations location is not a file: $jar" }
    return jar
}
