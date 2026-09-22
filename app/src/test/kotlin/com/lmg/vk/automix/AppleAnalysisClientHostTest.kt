package com.lmg.vk.automix

import com.lmg.vk.engine.automix.AppleAnalysisClient
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private class Reply(url: URL, val payload: ByteArray, val status: Int = 200, val declared: Long = -1) : HttpURLConnection(url) {
    var disconnected = false
    override fun connect() {}
    override fun disconnect() { disconnected = true }
    override fun usingProxy() = false
    override fun getResponseCode() = status
    override fun getContentLengthLong() = declared
    override fun getInputStream() = ByteArrayInputStream(payload)
}
private fun fails(block: () -> Unit) {
    var failed = false
    try { block() } catch (_: IOException) { failed = true }
    check(failed)
}
fun main() {
    val body = "{\"data\":[{\"id\":\"1488408568\",\"type\":\"songs\"}]}"
    lateinit var reply: Reply
    val client = AppleAnalysisClient { Reply(it, body.toByteArray()).also { reply = it } }
    val call = client.newCall("1488408568", "us", "test.token.value")
    check(call.execute() == body)
    check(call.url.host == "amp-api.music.apple.com")
    check(call.url.path == "/v1/catalog/us/songs/1488408568")
    check(call.url.query.contains("extend%5Baudio-analysis%5D=fades,loudnessCurve"))
    check(reply.getRequestProperty("Authorization") == "Bearer test.token.value")
    check(reply.getRequestProperty("Origin") == "https://music.apple.com")
    check(!reply.instanceFollowRedirects && reply.disconnected)
    for (status in listOf(302, 401, 403, 404, 429, 500)) {
        fails { AppleAnalysisClient { Reply(it, byteArrayOf(), status) }.newCall("1", "us", "token").execute() }
    }
    fails { AppleAnalysisClient { Reply(it, byteArrayOf(), declared = 4194305) }.newCall("1", "us", "token").execute() }
    fails { AppleAnalysisClient { Reply(it, ByteArray(4194305)) }.newCall("1", "us", "token").execute() }
    fails { AppleAnalysisClient { Reply(it, byteArrayOf(0xff.toByte())) }.newCall("1", "us", "token").execute() }
    val cancelled = client.newCall("1", "us", "token");cancelled.cancel();fails { cancelled.execute() }
    var rejected = false
    try { client.newCall("../songs", "us", "token") } catch (_: IllegalArgumentException) { rejected = true }
    check(rejected)
    println("Apple analysis request URL, headers, bounded body, failures and pre-cancellation passed")
}
