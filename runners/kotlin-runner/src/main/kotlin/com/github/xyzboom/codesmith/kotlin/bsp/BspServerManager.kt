package com.github.xyzboom.codesmith.kotlin.bsp

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

object BspServerManager : AutoCloseable {
    private const val DEFAULT_REVISION = "6487eec8d9bb0567faad40ee880c54ea84490b4d"
    private val logger = KotlinLogging.logger {}
    private var serverProcess: Process? = null
    private var allocatedPort: Int = -1
    private var shutdownHookInstalled = false

    val port: Int
        get() = startServer()

    val clientExecutable: File
        get() = ensureInstalled().resolve("client/build/install/client/bin/client")

    @Synchronized
    fun startServer(): Int {
        if (serverProcess?.isAlive == true && allocatedPort > 0) return allocatedPort

        val installation = ensureInstalled()
        val serverExecutable = installation.resolve("build/install/CompilerRunner/bin/CompilerRunner")
        allocatedPort = findAvailablePort()
        val logFile = File("out/bsp-server-$allocatedPort.log")
        logFile.parentFile.mkdirs()

        logger.info { "Starting CompilerRunner BSP server on port $allocatedPort" }
        val process = ProcessBuilder(
            serverExecutable.absolutePath,
            "--port",
            allocatedPort.toString(),
        ).redirectErrorStream(true)
            .redirectOutput(logFile)
            .start()
        serverProcess = process

        if (!shutdownHookInstalled) {
            Runtime.getRuntime().addShutdownHook(Thread { close() })
            shutdownHookInstalled = true
        }

        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            if (!process.isAlive) {
                error("CompilerRunner exited with ${process.exitValue()}; see ${logFile.absolutePath}")
            }
            if (canConnect(allocatedPort)) return allocatedPort
            Thread.sleep(100)
        }
        process.destroyForcibly()
        error("CompilerRunner did not listen on port $allocatedPort; see ${logFile.absolutePath}")
    }

    @Synchronized
    fun ensureInstalled(): File {
        val configuredHome = System.getenv("COMPILER_RUNNER_HOME")
            ?: System.getProperty("compiler.runner.home")
        if (configuredHome != null) return validateInstallation(File(configuredHome))

        val revision = System.getenv("COMPILER_RUNNER_REVISION")
            ?: System.getProperty("compiler.runner.revision")
            ?: DEFAULT_REVISION
        val cacheBase = System.getenv("COMPILER_RUNNER_CACHE_DIR")
            ?: System.getProperty("compiler.runner.cache.dir")
            ?: File(System.getProperty("user.home"), ".cache/cross-lang-fuzzer/compiler-runner").path
        val revisionDir = File(cacheBase, revision)
        val extractedRoot = revisionDir.resolve("CompilerRunner-$revision")
        val serverExecutable = extractedRoot.resolve("build/install/CompilerRunner/bin/CompilerRunner")
        val clientExecutable = extractedRoot.resolve("client/build/install/client/bin/client")
        if (serverExecutable.canExecute() && clientExecutable.canExecute()) return extractedRoot

        revisionDir.mkdirs()
        val archive = revisionDir.resolve("CompilerRunner-$revision.zip")
        if (!archive.isFile) {
            val url = URI("https://github.com/XYZboom/CompilerRunner/archive/$revision.zip").toURL()
            logger.info { "Downloading CompilerRunner $revision from $url" }
            url.openStream().use { input ->
                Files.copy(input, archive.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
        extractArchive(archive, revisionDir)

        val buildLog = revisionDir.resolve("install.log")
        val process = ProcessBuilder("./gradlew", "--no-daemon", "installDist")
            .directory(extractedRoot)
            .redirectErrorStream(true)
            .redirectOutput(buildLog)
            .start()
        check(process.waitFor() == 0) {
            "Failed to install CompilerRunner; see ${buildLog.absolutePath}"
        }
        return validateInstallation(extractedRoot)
    }

    private fun validateInstallation(root: File): File {
        val server = root.resolve("build/install/CompilerRunner/bin/CompilerRunner")
        val client = root.resolve("client/build/install/client/bin/client")
        check(server.canExecute() && client.canExecute()) {
            "Invalid CompilerRunner installation: ${root.absolutePath}"
        }
        return root
    }

    private fun extractArchive(archive: File, destination: File) {
        val destinationPath = destination.canonicalFile.toPath()
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = destination.resolve(entry.name).canonicalFile
                check(target.toPath().startsWith(destinationPath)) { "Unsafe archive entry: ${entry.name}" }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile.mkdirs()
                    target.outputStream().use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
            }
        }
        destination.resolve(archive.nameWithoutExtension).resolve("gradlew").setExecutable(true)
    }

    private fun findAvailablePort(): Int = ServerSocket(0).use { it.localPort }

    private fun canConnect(port: Int): Boolean = try {
        Socket("127.0.0.1", port).use { true }
    } catch (_: Throwable) {
        false
    }

    @Synchronized
    override fun close() {
        val process = serverProcess ?: return
        if (process.isAlive) {
            logger.info { "Stopping CompilerRunner BSP server (pid=${process.pid()})" }
            process.destroy()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(5, TimeUnit.SECONDS)
            }
        }
        serverProcess = null
        allocatedPort = -1
    }
}
