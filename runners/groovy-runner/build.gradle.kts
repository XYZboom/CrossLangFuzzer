import de.undercouch.gradle.tasks.download.Download
import java.net.URL
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.system.exitProcess

plugins {
    kotlin("jvm")
    application
    id("de.undercouch.download") version "5.6.0"
}

group = "com.github.xyzboom"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project.rootProject)
//    implementation("org.apache.groovy:groovy:4.0.27")
    implementation(project(":runners:common-runner"))
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation(kotlin("stdlib-jdk8"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(8)
}

val groovyVersions: List<String> by project.extra {
    if (project.hasProperty("groovyVersions")) {
        (project.property("groovyVersions") as String)
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    } else {
        listOf("4.0.27", "5.0.0-beta-1")
    }
}

val downloadDir = layout.buildDirectory.dir("groovy-sdk")
val unzipTasks = mutableListOf<Task>()

groovyVersions.forEach { version ->
    val downloadTask = tasks.register<Download>("downloadGroovySdk_${version.replace('.', '_')}") {

        val zipFileName = "apache-groovy-binary-$version.zip"
        val downloadUrl = "https://archive.apache.org/dist/groovy/$version/distribution/$zipFileName"

        src(downloadUrl)
        dest(downloadDir.map { it.file(zipFileName) })
        overwrite(false)
        onlyIfModified(true)

        doFirst {
            logger.lifecycle("Downloading Groovy $version...")
            logger.lifecycle("From: $downloadUrl")
        }

        doLast {
            logger.lifecycle("Download finished: Groovy $version")
            logger.lifecycle("Location: ${dest.absolutePath}")
        }
    }

    val unzipTask = tasks.register<Copy>("unzipGroovySdk_${version.replace('.', '_')}") {
        dependsOn(downloadTask)
        mustRunAfter(downloadTask)

        val zipFile = downloadDir.get().file("apache-groovy-binary-$version.zip")
        val targetDir = downloadDir.map { it.dir("groovy-$version") }

        from(zipTree(zipFile)) {
            // Remap file paths to skip the top-level directory
            eachFile {
                val pathSegments = relativePath.segments
                if (pathSegments.size > 1) {
                    // Remove the first directory level (typically the version-numbered folder)
                    relativePath = RelativePath(true, *pathSegments.drop(1).toTypedArray())
                } else {
                    // Exclude the top-level directory itself (no files copied from root)
                    exclude()
                }
            }
            includeEmptyDirs = false
        }
        into(targetDir)

        doFirst {
            logger.lifecycle("Unzipping Groovy $version...")
        }

        doLast {
            logger.lifecycle("Unzip finished: Groovy $version")
            logger.lifecycle("Location: ${targetDir.get().asFile.absolutePath}")

            val groovyDir = targetDir.get().asFile
            if (!groovyDir.resolve("bin/groovy").exists()) {
                logger.warn("Warning: Groovy $version may have unzip error - bin/groovy not found")
            } else {
                logger.lifecycle("Verified: bin/groovy exists")
            }
        }
    }

    unzipTasks.add(unzipTask.get())
}

val setupAllGroovySdks = tasks.register("setupAllGroovySdks") {
    group = "Groovy SDK"
    description = "Download and unzip all Groovy SDKs"
    dependsOn(unzipTasks)

    doLast {
        logger.lifecycle("All Groovy SDKs setup finished")
        logger.lifecycle("Versions: ${groovyVersions.joinToString()}")
        logger.lifecycle("Locations: ${downloadDir.get().asFile.absolutePath}\n")
    }
}

tasks.withType<JavaExec> {
    dependsOn(setupAllGroovySdks)
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(11)
    }
    mainClass.set("com.github.xyzboom.codesmith.groovy.CodeSmithGroovyRunnerKt")
    systemProperties["codesmith.logger.console"] = System.getProperty("codesmith.logger.console") ?: "info"
    systemProperties["codesmith.logger.traceFile"] = System.getProperty("codesmith.logger.traceFile") ?: "off"
    systemProperties["codesmith.logger.traceFile.ImmediateFlush"] =
        System.getProperty("codesmith.logger.traceFile.ImmediateFlush") ?: "false"
    val tmpPath = System.getProperty("java.io.tmpdir")
    if (tmpPath != null) {
        systemProperties["java.io.tmpdir"] = tmpPath
    }
    systemProperties["codesmith.logger.outdir"] = System.getProperty("codesmith.logger.outdir") ?: "./out"
    systemProperties["groovy.sdk.dir"] = downloadDir.get().asFile.absolutePath
}