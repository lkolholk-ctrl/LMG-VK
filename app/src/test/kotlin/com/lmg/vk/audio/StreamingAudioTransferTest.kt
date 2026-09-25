package com.lmg.vk.audio

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class StreamingAudioTransferTest {
    @get:Rule val temp = TemporaryFolder()

    private fun server(response: (java.net.Socket) -> Unit, test: (String) -> Unit) {
        val server = ServerSocket(0)
        val worker = thread(isDaemon = true) {
            runCatching { server.accept().use { socket ->
                val input = socket.getInputStream().bufferedReader()
                while (!input.readLine().isNullOrEmpty()) { }
                response(socket)
            } }
        }
        try { test("http://127.0.0.1:${server.localPort}/audio") }
        finally { server.close(); worker.join(1000) }
    }
    private fun send(socket: java.net.Socket, status: Int, type: String, bytes: ByteArray, declared: Int = bytes.size) {
        socket.getOutputStream().apply {
            write("HTTP/1.1 $status Test\r\nContent-Type: $type\r\nContent-Length: $declared\r\nConnection: close\r\n\r\n".toByteArray())
            write(bytes); flush()
        }
    }
    @Test fun streamsExactPayloadAndReportsCompletion() {
        val bytes = ByteArray(256_000) { (it % 251).toByte() }
        server({ send(it, 200, "audio/mpeg", bytes) }) { url -> runBlocking {
            val client = HttpClient(OkHttp)
            try {
                val out = temp.newFile(); var progress = 0f
                StreamingAudioTransfer.download(client, url, out) { progress = it }
                assertArrayEquals(bytes, out.readBytes()); assertEquals(1f, progress, 0f)
            } finally { client.close() }
        } }
    }
    @Test fun interruptedResponseIsRemoved() {
        server({ send(it, 200, "audio/mpeg", ByteArray(100), 10000) }) { url -> runBlocking {
            val client = HttpClient(OkHttp); val out = temp.newFile()
            try {
                assertTrue(runCatching { StreamingAudioTransfer.download(client, url, out) {} }.isFailure)
                assertFalse(out.exists())
            } finally { client.close() }
        } }
    }
    @Test fun errorAndPlaylistAreNeverSavedAsAudio() {
        for ((status, type) in listOf(403 to "audio/mpeg", 200 to "application/vnd.apple.mpegurl", 200 to "text/html")) {
            server({ send(it, status, type, "not music".toByteArray()) }) { url -> runBlocking {
                val client = HttpClient(OkHttp); val out = temp.newFile()
                try {
                    assertTrue(runCatching { StreamingAudioTransfer.download(client, url, out) {} }.isFailure)
                    assertFalse(out.exists())
                } finally { client.close() }
            } }
        }
    }
    @Test fun cancellationInterruptsTransferAndDeletesPartialFile() {
        val started = CompletableDeferred<Unit>()
        val finish = CountDownLatch(1)
        server({ socket ->
            send(socket, 200, "audio/mpeg", ByteArray(64000), 1000000)
            finish.await(5, TimeUnit.SECONDS)
        }) { url -> runBlocking {
            val client = HttpClient(OkHttp); val out = temp.newFile()
            try {
                val job = launch(Dispatchers.IO) { StreamingAudioTransfer.download(client, url, out) { started.complete(Unit) } }
                withTimeout(5000) { started.await() }
                job.cancelAndJoin()
                assertFalse(out.exists())
            } finally { finish.countDown(); client.close() }
        } }
    }
}
