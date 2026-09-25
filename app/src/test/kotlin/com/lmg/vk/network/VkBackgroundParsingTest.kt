package com.lmg.vk.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class VkBackgroundParsingTest {
    private val raw = object : RawHttpResponse {
        override val statusCode = 200
        override val url = "https://example.invalid/method/test"
        override suspend fun bodyText() = "payload"
    }

    @Test fun decodingAndMappingRunOffCallerAndResumeOnCaller() {
        Executors.newSingleThreadExecutor { Thread(it, "vk-test-caller") }.asCoroutineDispatcher().use { caller ->
            runBlocking(caller) {
                val originalThread = Thread.currentThread()
                val parser = MappingVkResponseParser(
                    VkResponseParser { response ->
                        assertNotSame(originalThread, Thread.currentThread())
                        VkParsedResponse(response.bodyText(), null)
                    },
                ) { body ->
                    assertNotSame(originalThread, Thread.currentThread())
                    body.uppercase()
                }
                val result = parser.parseInBackground(raw)
                assertEquals("PAYLOAD", result.data)
                assertSame(originalThread, Thread.currentThread())
            }
        }
    }

    @Test fun errorEnvelopesAndDecodeExceptionsArePreserved() = runBlocking {
        val expected = VkParsedResponse<String>(null, com.lmg.vk.network.dto.VKError(error_code = 14, error_msg = "captcha"))
        assertSame(expected, VkResponseParser<String> { expected }.parseInBackground(raw))
        val failure = IllegalArgumentException("bad response")
        val parser = VkResponseParser<String> { throw failure }
        try {
            parser.parseInBackground(raw)
            fail("Decode exception was swallowed")
        } catch (error: IllegalArgumentException) {
            assertEquals(failure.message, error.message)
        }
    }

    @Test fun cancellingDuringSynchronousDecodeDoesNotPublishItsResult() {
        Executors.newSingleThreadExecutor { Thread(it, "vk-test-caller") }.asCoroutineDispatcher().use { caller ->
            runBlocking {
                val entered = CompletableDeferred<Unit>()
                val release = CountDownLatch(1)
                var delivered = false
                val job = launch(caller) {
                    val parser = VkResponseParser {
                        entered.complete(Unit)
                        check(release.await(5, TimeUnit.SECONDS))
                        VkParsedResponse("stale", null)
                    }
                    parser.parseInBackground(raw)
                    delivered = true
                }
                try {
                    withTimeout(5_000) { entered.await() }
                    job.cancel()
                } finally {
                    release.countDown()
                    job.cancelAndJoin()
                }
                assertTrue(job.isCancelled)
                assertFalse(delivered)
            }
        }
    }
}
