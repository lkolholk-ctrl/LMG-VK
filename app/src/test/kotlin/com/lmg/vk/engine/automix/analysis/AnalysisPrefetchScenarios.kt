package com.lmg.vk.engine.automix.analysis

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*

/** Recording HTTP transport, not the deployed proxy and not the native decoder. */
private class HttpReply(
    url: URL,
    private val body: ByteArray = "{\"data\":[]}".toByteArray(),
    private val status: Int = 200,
    private val id: String? = "123",
    private val length: Long = -1,
    private val mime: String? = "application/json; charset=utf-8",
    private val encoding: String? = null,
    private val onRead: (() -> Unit)? = null,
) : HttpURLConnection(url) {
    var disconnected = false
    var readOpened = false
    override fun connect() = Unit
    override fun disconnect() { disconnected = true }
    override fun usingProxy() = false
    override fun getResponseCode() = status
    override fun getContentLengthLong() = length
    override fun getContentType() = mime
    override fun getHeaderField(name: String?): String? = when (name) {
        "X-Track-Id" -> id
        "Content-Encoding" -> encoding
        else -> null
    }
    override fun getInputStream(): InputStream {
        readOpened = true
        onRead?.invoke()
        return ByteArrayInputStream(body)
    }
}
private fun expectFailure(r: AnalysisResult, failure: AnalysisFailure) {
    check(r is AnalysisResult.Failed && r.reason == failure) { "Expected $failure, got $r" }
}
private fun invalid(block: () -> Unit) {
    var rejected = false
    try { block() } catch (_: IllegalArgumentException) { rejected = true }
    check(rejected)
}
private fun ready(id: String = "123", bytes: Int = 20) = AnalysisResult.Ready(RawAnalysis(id, ByteArray(bytes) { 65 }))
private class FakeCall(private val result: AnalysisResult, private val count: AtomicInteger) : AnalysisHttpCall {
    override fun execute(): AnalysisResult { count.incrementAndGet(); return result }
    override fun cancel() = Unit
}
private class BlockingCall(private val result: AnalysisResult) : AnalysisHttpCall {
    val entered = CompletableDeferred<Unit>()
    private val latch = CountDownLatch(1)
    val cancels = AtomicInteger()
    override fun execute(): AnalysisResult {
        entered.complete(Unit)
        check(latch.await(10, TimeUnit.SECONDS)) { "Test fixture timed out" }
        return result // Deliberately returns late even after cancellation.
    }
    override fun cancel() { cancels.incrementAndGet(); latch.countDown() }
    fun finish() { latch.countDown() }
}
private suspend fun until(condition: () -> Boolean) {
    withTimeout(5000) { while (!condition()) { yield(); delay(1) } }
}
private class Token(val label: Int)

object AnalysisPrefetchScenarios {
    private var groups = 0
    private fun group(block: () -> Unit) { block(); groups++ }
    private suspend fun sgroup(block: suspend CoroutineScope.() -> Unit) { coroutineScope { block() }; groups++ }

    suspend fun runAll(): Int {
        groups = 0
        group {
            lateinit var reply: HttpReply
            val r = RuAutoMixAnalysisClient({ "test-key" }) { url -> HttpReply(url).also { reply = it } }
                .newCall(AnalysisLookup.ById("123")).execute()
            check(r is AnalysisResult.Ready)
            check(reply.url.protocol == "https" && reply.url.host == "ru-lyrics.gsgit.org")
            check(reply.url.path == "/v2/automix/analysis" && reply.url.query == "id=123&raw=1")
            check(reply.getRequestProperty("X-API-Key") == "test-key")
            check(reply.getRequestProperty("Authorization") == null)
            check(reply.getRequestProperty("Accept-Encoding") == "identity")
            check(!reply.instanceFollowRedirects && !reply.useCaches && reply.disconnected)
            check(reply.connectTimeout == 10000 && reply.readTimeout == 15000)
        }
        group {
            val old = Locale.getDefault()
            try {
                Locale.setDefault(Locale.FRANCE)
                val lookup = AnalysisLookup.ByMetadata("Песня &raw=0 / ?", "Артист + duo", 123456)
                val u = RuAutoMixAnalysisClient.requestUrl(lookup)
                val args = u.query.split('&').associate { v ->
                    val s = v.split('=', limit = 2)
                    URLDecoder.decode(s[0], "UTF-8") to URLDecoder.decode(s[1], "UTF-8")
                }
                check(args.size == 4 && args["raw"] == "1" && args["duration"] == "123.456")
                check(args["title"] == lookup.title && args["artist"] == lookup.artist)
            } finally { Locale.setDefault(old) }
        }
        group {
            for (id in listOf("", "../1", "123&raw=0", "-1", "１２３", "1".repeat(33))) invalid { AnalysisLookup.ById(id) }
            for (text in listOf("", " ", "bad\nname", "x".repeat(1025), "\uD800"))
                invalid { AnalysisLookup.ByMetadata(text, "A", 1000) }
            for (d in listOf(0L, -1, Long.MAX_VALUE)) invalid { AnalysisLookup.ByMetadata("T", "A", d) }
        }
        group {
            for (key in listOf(null, "", "bad\r\nheader", "x".repeat(4097))) {
                var called = false
                val c = RuAutoMixAnalysisClient({ key }) { called = true; HttpReply(it) }
                expectFailure(c.newCall(AnalysisLookup.ById("123")).execute(), AnalysisFailure.NO_CREDENTIAL)
                check(!called)
            }
        }
        group {
            val failures = mapOf(301 to AnalysisFailure.REDIRECT_REJECTED, 302 to AnalysisFailure.REDIRECT_REJECTED,
                401 to AnalysisFailure.UNAUTHORIZED, 403 to AnalysisFailure.UNAUTHORIZED,
                404 to AnalysisFailure.NOT_FOUND, 429 to AnalysisFailure.RATE_LIMITED,
                500 to AnalysisFailure.SERVER_ERROR, 503 to AnalysisFailure.SERVER_ERROR,
                400 to AnalysisFailure.HTTP_ERROR)
            failures.forEach { (code, reason) ->
                lateinit var conn: HttpReply
                val r = RuAutoMixAnalysisClient({ "test-key" }) { HttpReply(it, status = code).also { c -> conn = c } }
                    .newCall(AnalysisLookup.ById("123")).execute()
                expectFailure(r, reason); check(!conn.readOpened && conn.disconnected)
            }
        }
        group {
            for (id in listOf(null, "", "bad", "124")) {
                val r = RuAutoMixAnalysisClient({ "test-key" }) { HttpReply(it, id = id) }.newCall(AnalysisLookup.ById("123")).execute()
                expectFailure(r, AnalysisFailure.INVALID_RESPONSE)
            }
        }
        group {
            val l = AnalysisLookup.ByMetadata("T", "A", 1000)
            val r = RuAutoMixAnalysisClient({ "test-key" }) { HttpReply(it, id = "987") }.newCall(l).execute()
            check(r is AnalysisResult.Ready && r.analysis.songId == "987")
        }
        group {
            lateinit var conn: HttpReply
            val r = RuAutoMixAnalysisClient({ "test-key" }) {
                HttpReply(it, length = RuAutoMixAnalysisClient.MAX_BYTES + 1L).also { c -> conn = c }
            }.newCall(AnalysisLookup.ById("123")).execute()
            expectFailure(r, AnalysisFailure.TOO_LARGE); check(!conn.readOpened)
        }
        group {
            val r = RuAutoMixAnalysisClient({ "test-key" }) {
                HttpReply(it, body = ByteArray(RuAutoMixAnalysisClient.MAX_BYTES + 1))
            }.newCall(AnalysisLookup.ById("123")).execute()
            expectFailure(r, AnalysisFailure.TOO_LARGE)
        }
        group {
            for (data in listOf(byteArrayOf(), byteArrayOf(0xff.toByte()))) {
                expectFailure(RuAutoMixAnalysisClient({ "test-key" }) { HttpReply(it, body = data) }
                    .newCall(AnalysisLookup.ById("123")).execute(), AnalysisFailure.INVALID_RESPONSE)
            }
            expectFailure(RuAutoMixAnalysisClient({ "test-key" }) { HttpReply(it, length = 100) }
                .newCall(AnalysisLookup.ById("123")).execute(), AnalysisFailure.INVALID_RESPONSE)
        }
        group {
            expectFailure(RuAutoMixAnalysisClient({ "test-key" }) { HttpReply(it, mime = "text/html") }
                .newCall(AnalysisLookup.ById("123")).execute(), AnalysisFailure.INVALID_RESPONSE)
            expectFailure(RuAutoMixAnalysisClient({ "test-key" }) { HttpReply(it, encoding = "gzip") }
                .newCall(AnalysisLookup.ById("123")).execute(), AnalysisFailure.INVALID_RESPONSE)
        }
        group {
            var opened = 0
            val call = RuAutoMixAnalysisClient({ "test-key" }) { opened++; HttpReply(it) }.newCall(AnalysisLookup.ById("123"))
            call.cancel(); expectFailure(call.execute(), AnalysisFailure.CANCELLED); check(opened == 0)
            var failed = false
            try { call.execute() } catch (_: IllegalStateException) { failed = true }
            check(failed)
        }
        group {
            val bytes = byteArrayOf(1, 2, 3)
            val r = RawAnalysis("123", bytes); bytes[0] = 99
            val copy = r.copyBytes(); copy[1] = 99
            check(r.copyBytes().contentEquals(byteArrayOf(1, 2, 3)))
            check(!r.toString().contains("123") && !AnalysisLookup.ByMetadata("SECRET", "NAME", 1000).toString().contains("SECRET"))
        }
        sgroup {
            val count = AtomicInteger(); var now = 10L; var parses = 0
            val store = AnalysisPrefetchStore(this, { FakeCall(ready(), count) }, { parses++; null }, { now })
            try {
                check(store.get(AnalysisLookup.ById("123")) is AnalysisResult.Ready)
                check(store.get(AnalysisLookup.ById("123")) is AnalysisResult.Ready)
                check(count.get() == 1 && parses == 1)
                now += 600_001; store.get(AnalysisLookup.ById("123")); check(count.get() == 2)
            } finally { store.close() }
        }
        sgroup {
            val count = AtomicInteger(); var now = 0L
            val store = AnalysisPrefetchStore(this, { FakeCall(AnalysisResult.Failed(AnalysisFailure.NOT_FOUND, 404), count) }, { error("No parse on 404") }, { now })
            try {
                repeat(2) { expectFailure(store.get(AnalysisLookup.ById("123")), AnalysisFailure.NOT_FOUND) }
                check(count.get() == 1); now = 30_000
                store.get(AnalysisLookup.ById("123")); check(count.get() == 2)
            } finally { store.close() }
        }
        sgroup {
            for (failure in listOf(AnalysisFailure.UNAUTHORIZED, AnalysisFailure.NETWORK_ERROR, AnalysisFailure.RATE_LIMITED)) {
                val count = AtomicInteger()
                val store = AnalysisPrefetchStore(this, { FakeCall(AnalysisResult.Failed(failure), count) }, { null })
                try { repeat(2) { store.get(AnalysisLookup.ById("123")) }; check(count.get() == 2) }
                finally { store.close() }
            }
        }
        sgroup {
            val count = AtomicInteger()
            val store = AnalysisPrefetchStore(this, { FakeCall(ready(), count) }, { AnalysisFailure.INVALID_RESPONSE })
            try {
                repeat(2) { expectFailure(store.get(AnalysisLookup.ById("123")), AnalysisFailure.INVALID_RESPONSE) }
                check(count.get() == 2)
            } finally { store.close() }
        }
        sgroup {
            val count = AtomicInteger()
            val store = AnalysisPrefetchStore(this, { FakeCall(ready(bytes = 20), count) }, { null }, maximumBytes = 25, maximumEntries = 5)
            try {
                store.get(AnalysisLookup.ById("1")); store.get(AnalysisLookup.ById("2")); store.get(AnalysisLookup.ById("1"))
                check(count.get() == 3)
            } finally { store.close() }
        }
        sgroup {
            val count = AtomicInteger()
            val store = AnalysisPrefetchStore(this, { FakeCall(ready(bytes = 20), count) }, { null }, maximumBytes = 10)
            try { repeat(2) { store.get(AnalysisLookup.ById("1")) }; check(count.get() == 2) }
            finally { store.close() }
        }
        sgroup {
            val count = AtomicInteger(); val call = BlockingCall(ready())
            val store = AnalysisPrefetchStore(this, { count.incrementAndGet(); call }, { null })
            try {
                val a = async { store.get(AnalysisLookup.ById("123")) }
                val b = async { store.get(AnalysisLookup.ById("123")) }
                call.entered.await(); yield(); a.cancelAndJoin()
                check(count.get() == 1 && call.cancels.get() == 0)
                call.finish(); check(b.await() is AnalysisResult.Ready)
                check(count.get() == 1)
            } finally { call.finish(); store.close() }
        }
        sgroup {
            var calls = 0; val block = BlockingCall(ready())
            val store = AnalysisPrefetchStore(this, { calls++; if (calls == 1) block else FakeCall(ready(), AtomicInteger()) }, { null })
            try {
                val a = async { store.get(AnalysisLookup.ById("123")) }
                block.entered.await(); a.cancelAndJoin(); until { block.cancels.get() > 0 }
                store.get(AnalysisLookup.ById("123")); check(calls == 2)
            } finally { block.finish(); store.close() }
        }
        sgroup {
            val count = AtomicInteger()
            val store = AnalysisPrefetchStore(this, { FakeCall(ready(), count) }, { null })
            var submitted = 0; val statuses = mutableListOf<PrefetchStatus>()
            val c = AnalysisPrefetchCoordinator<Token>(this, store, { _, _, _ -> submitted++; true }, { statuses += it.status })
            try {
                c.update(1, listOf(PrefetchRequest(Token(1), AnalysisLookup.ByMetadata("T", "A", 1000), null)), true)
                until { PrefetchStatus.CANDIDATE_ONLY in statuses }
                check(submitted == 0 && count.get() == 1)
            } finally { c.close(); store.close() }
        }
        sgroup {
            val count = AtomicInteger(); val token = Token(1)
            val store = AnalysisPrefetchStore(this, { FakeCall(ready(), count) }, { null })
            val submitted = mutableListOf<Token>()
            val c = AnalysisPrefetchCoordinator<Token>(this, store, { t, id, b ->
                check(t === token && id == "123" && b.size == 20); submitted += t; true
            })
            try {
                val request = PrefetchRequest(token, AnalysisLookup.ById("123"), "123")
                repeat(5) { c.update(1, listOf(request), true); yield() }
                until { submitted.size == 1 }
                check(count.get() == 1)
            } finally { c.close(); store.close() }
        }
        sgroup {
            val count = AtomicInteger(); val a = Token(1); val b = Token(1)
            val store = AnalysisPrefetchStore(this, { FakeCall(ready(), count) }, { null })
            val submitted = mutableListOf<Token>()
            val c = AnalysisPrefetchCoordinator<Token>(this, store, { t, _, _ -> submitted += t; true })
            try {
                c.update(1, listOf(PrefetchRequest(a, AnalysisLookup.ById("123"), "123"), PrefetchRequest(b, AnalysisLookup.ById("123"), "123")), true)
                until { submitted.size == 2 }
                check(submitted.any { it === a } && submitted.any { it === b } && count.get() == 1)
            } finally { c.close(); store.close() }
        }
        sgroup {
            val count = AtomicInteger(); val store = AnalysisPrefetchStore(this, { FakeCall(ready(), count) }, { null })
            var submitted = 0; val c = AnalysisPrefetchCoordinator<Token>(this, store, { _, _, _ -> submitted++; true })
            try {
                c.update(1, listOf(PrefetchRequest(Token(1), AnalysisLookup.ById("123"), "123")), false)
                yield(); check(count.get() == 0 && submitted == 0)
            } finally { c.close(); store.close() }
        }
        sgroup {
            val first = BlockingCall(ready()); var count = 0
            val store = AnalysisPrefetchStore(this, { count++; if (count == 1) first else FakeCall(ready(), AtomicInteger()) }, { null })
            val submitted = mutableListOf<Token>(); val a = Token(1); val b = Token(2)
            val c = AnalysisPrefetchCoordinator<Token>(this, store, { t, _, _ -> submitted += t; true })
            try {
                c.update(1, listOf(PrefetchRequest(a, AnalysisLookup.ById("123"), "123")), true)
                first.entered.await()
                c.update(2, listOf(PrefetchRequest(b, AnalysisLookup.ById("123"), "123")), true)
                until { submitted.isNotEmpty() }
                check(submitted.size == 1 && submitted[0] === b)
            } finally { first.finish(); c.close(); store.close() }
        }
        sgroup {
            val first = BlockingCall(ready())
            val store = AnalysisPrefetchStore(this, { first }, { null })
            var submitted = 0; val c = AnalysisPrefetchCoordinator<Token>(this, store, { _, _, _ -> submitted++; true })
            try {
                c.update(1, listOf(PrefetchRequest(Token(1), AnalysisLookup.ById("123"), "123")), true)
                first.entered.await(); c.close(); first.finish(); yield(); delay(10)
                check(submitted == 0)
            } finally { first.finish(); store.close() }
        }
        sgroup {
            val count = AtomicInteger(); val statuses = mutableListOf<PrefetchStatus>()
            val store = AnalysisPrefetchStore(this, { FakeCall(ready(), count) }, { null })
            val c = AnalysisPrefetchCoordinator<Token>(this, store, { _, _, _ -> false }, { statuses += it.status })
            try {
                c.update(1, listOf(PrefetchRequest(Token(1), AnalysisLookup.ById("123"), "123")), true)
                until { PrefetchStatus.STALE in statuses }; check(count.get() == 1)
            } finally { c.close(); store.close() }
        }
        sgroup {
            val count = AtomicInteger(); val notices = mutableListOf<PrefetchNotice<Token>>()
            val store = AnalysisPrefetchStore(this, { FakeCall(AnalysisResult.Failed(AnalysisFailure.NOT_FOUND), count) }, { null })
            val c = AnalysisPrefetchCoordinator<Token>(this, store, { _, _, _ -> error("404 must not submit") }, { notices += it })
            try {
                c.update(1, listOf(PrefetchRequest(Token(1), AnalysisLookup.ById("123"), "123")), true)
                until { notices.any { it.failure == AnalysisFailure.NOT_FOUND } }
                check(notices.last().status == PrefetchStatus.UNAVAILABLE)
            } finally { c.close(); store.close() }
        }
        group {
            invalid { PrefetchRequest(Token(1), AnalysisLookup.ByMetadata("T", "A", 1000), "123") }
            invalid { PrefetchRequest(Token(1), AnalysisLookup.ById("123"), "456") }
        }
        sgroup {
            val block = BlockingCall(ready())
            val store = AnalysisPrefetchStore(this, { block }, { null }, timeoutMs = 40)
            try {
                expectFailure(store.get(AnalysisLookup.ById("123")), AnalysisFailure.TIMEOUT)
                check(block.cancels.get() > 0)
            } finally { block.finish(); store.close() }
        }
        sgroup {
            val count = AtomicInteger(); var validations = 0
            val store = AnalysisPrefetchStore(this, { FakeCall(ready(), count) }, {
                validations++; AnalysisFailure.NATIVE_UNAVAILABLE
            })
            try {
                repeat(2) { expectFailure(store.get(AnalysisLookup.ById("123")), AnalysisFailure.NATIVE_UNAVAILABLE) }
                check(validations == 2 && count.get() == 2)
            } finally { store.close() }
        }
        println("Analysis transport/cache/lookahead: $groups groups passed (fake HTTP and injected validator; no live endpoint or native-parser run)")
        return groups
    }
}
fun main() = runBlocking { AnalysisPrefetchScenarios.runAll(); Unit }
