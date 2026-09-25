package com.lmg.vk.debug

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PerformanceCaptureFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun store(): PerformanceCaptureFiles = PerformanceCaptureFiles(
        File(temporary.root, "files").apply { mkdirs() },
        File(temporary.root, "cache").apply { mkdirs() },
    )

    private fun source(name: String = "trace.perfetto-trace"): File =
        File(temporary.root, "files/profiling/$name").apply {
            checkNotNull(parentFile).mkdirs()
            writeBytes(byteArrayOf(10, 3, 1, 2, 3))
        }

    @Test fun exportKeepsTraceBytesAndIncludesTheRecordedContext() {
        val store = store()
        val input = source()
        val saved = store.save("lmg-start-1750000000000", input, "requestElapsedMs=123", "frames=60")
        val archive = store.export(saved)
        ZipFile(archive).use { zip ->
            assertEquals(setOf("capture.perfetto-trace", "capture.txt", "debug-log.txt"),
                zip.entries().asSequence().map { it.name }.toSet())
            assertArrayEquals(input.readBytes(), zip.getInputStream(zip.getEntry("capture.perfetto-trace")).readBytes())
            assertEquals("requestElapsedMs=123", zip.getInputStream(zip.getEntry("capture.txt")).reader().readText())
            assertEquals("frames=60", zip.getInputStream(zip.getEntry("debug-log.txt")).reader().readText())
        }
        assertEquals(saved, store.latest())
        assertEquals(archive, store.export(saved))
    }

    @Test fun emptyNewResultDoesNotReplacePreviousSuccessfulCapture() {
        val store = store()
        val saved = store.save("lmg-start-1750000000000", source(), "details", "log")
        val empty = source("empty").apply { writeBytes(byteArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) {
            store.save("lmg-start-1750000000001", empty, "details", "log")
        }
        assertEquals(saved, store.latest())
        assertTrue(File(saved, "capture.perfetto-trace").length() > 0)
    }

    @Test fun rejectsPathsOutsideProfilingDirectoryIncludingSymlinks() {
        val store = store()
        source()
        val outside = File(temporary.root, "files/other").apply { writeText("private") }
        assertThrows(IllegalArgumentException::class.java) {
            store.save("lmg-start-1750000000000", outside, "details", "log")
        }
        val link = File(temporary.root, "files/profiling/link")
        Files.createSymbolicLink(link.toPath(), outside.toPath())
        assertThrows(IllegalArgumentException::class.java) {
            store.save("lmg-start-1750000000000", link, "details", "log")
        }
        assertNull(store.latest())
    }

    @Test fun incompleteAndStagingDirectoriesAreNotOfferedForSharing() {
        val store = store()
        val root = File(temporary.root, "files/performance-captures")
        for (name in listOf("lmg-start-1750000000000", "lmg-start-1750000000001.partial")) {
            File(root, "$name/capture.perfetto-trace").apply { checkNotNull(parentFile).mkdirs(); writeText("trace") }
        }
        assertNull(store.latest())
        assertThrows(IllegalArgumentException::class.java) {
            store.export(File(root, "lmg-start-1750000000000"))
        }
    }

    @Test fun keepsOnlyTwoCompletedCapturesAndLeavesPlatformOriginalsUntouched() {
        val store = store()
        val input = source()
        val first = store.save("lmg-start-1750000000000", input, "details", "log")
        val second = store.save("lmg-start-1750000000001", input, "details", "log")
        val third = store.save("lmg-start-1750000000002", input, "details", "log")
        assertFalse(first.exists())
        assertTrue(second.isDirectory)
        assertEquals(third, store.latest())
        assertTrue(input.isFile)
        assertEquals(third, store().latest())
    }

    @Test fun rejectsInvalidSessionNames() {
        val store = store()
        val input = source()
        for (tag in listOf("../outside", "lmg-start-invalid", "lmg-start-1750000000000/child")) {
            assertThrows(IllegalArgumentException::class.java) { store.save(tag, input, "details", "log") }
        }
    }
}
