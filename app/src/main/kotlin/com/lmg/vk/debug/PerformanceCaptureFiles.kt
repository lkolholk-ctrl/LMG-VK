package com.lmg.vk.debug

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class PerformanceCaptureFiles(private val filesDir: File, private val cacheDir: File) {
    private val root get() = File(filesDir, "performance-captures")

    fun latest(): File? = root.listFiles().orEmpty()
        .filter { it.name.matches(TAG) && complete(it) }
        .maxByOrNull { it.name }

    fun save(tag: String, source: File, details: String, log: String): File {
        require(tag.matches(TAG))
        val profilingRoot = File(filesDir, "profiling").canonicalFile
        val input = source.canonicalFile
        require(input.toPath().startsWith(profilingRoot.toPath()))
        require(input.isFile && input.length() > 0L)
        check(root.mkdirs() || root.isDirectory)
        val target = File(root, tag)
        if (complete(target)) return target
        val staging = File(root, "$tag.partial")
        staging.deleteRecursively()
        check(staging.mkdirs())
        try {
            input.copyTo(File(staging, "capture.perfetto-trace"))
            File(staging, "capture.txt").writeText(details)
            File(staging, "debug-log.txt").writeText(log)
            check(staging.renameTo(target))
            root.listFiles().orEmpty()
                .filter { it.name.matches(TAG) && complete(it) }
                .sortedByDescending { it.name }
                .drop(2)
                .forEach { it.deleteRecursively() }
            return target
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
    }

    fun export(capture: File): File {
        require(capture.canonicalFile.parentFile == root.canonicalFile && complete(capture))
        val exports = File(cacheDir, "logs").apply { check(mkdirs() || isDirectory) }
        val target = File(exports, "${capture.name}.zip")
        val staging = File(exports, "${capture.name}.zip.partial")
        try {
            ZipOutputStream(staging.outputStream().buffered()).use { zip ->
                for (name in listOf("capture.perfetto-trace", "capture.txt", "debug-log.txt")) {
                    zip.putNextEntry(ZipEntry(name))
                    File(capture, name).inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            check(staging.renameTo(target))
            return target
        } catch (error: Exception) {
            staging.delete()
            throw error
        }
    }

    private fun complete(dir: File) =
        File(dir, "capture.perfetto-trace").let { it.isFile && it.length() > 0L } &&
            File(dir, "capture.txt").isFile && File(dir, "debug-log.txt").isFile

    private companion object {
        val TAG = Regex("lmg-start-[0-9]{13}")
    }
}
