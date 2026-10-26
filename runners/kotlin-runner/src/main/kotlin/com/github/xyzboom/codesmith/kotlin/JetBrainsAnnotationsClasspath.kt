package com.github.xyzboom.codesmith.kotlin

import org.jetbrains.annotations.NotNull
import java.io.File
import java.lang.annotation.ElementType
import java.lang.annotation.Target
import java.net.URL

/** Returns the pinned JetBrains annotations artifact used by generated-source compiles. */
internal fun jetbrainsAnnotationsJar(): File {
    val annotationClass = NotNull::class.java
    val targets = annotationClass.getAnnotation(Target::class.java)?.value.orEmpty()
    require(ElementType.TYPE_USE in targets) {
        "Resolved org.jetbrains.annotations.NotNull does not support TYPE_USE"
    }

    return annotationsJarFromCodeSource(
        location = annotationClass.protectionDomain.codeSource?.location,
        classLoader = annotationClass.classLoader,
        classpath = System.getProperty("java.class.path") ?: "<unset>",
    )
}

internal fun annotationsJarFromCodeSource(location: URL?, classLoader: ClassLoader?, classpath: String): File {
    val resolvedLocation = location
        ?: error(
            "Cannot locate the resolved org.jetbrains:annotations artifact " +
                "(classLoader=${classLoader ?: "bootstrap"}, java.class.path=$classpath)"
        )
    val jar = File(resolvedLocation.toURI())
    require(jar.isFile) { "Resolved JetBrains annotations location is not a file: $jar" }
    return jar
}
