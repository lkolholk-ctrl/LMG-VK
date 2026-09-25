package com.lmg.vk.artwork

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.serialization.json.*

/** A manifest is published only after all media bytes have been committed. No TTL. */
internal class OfflineMotionIndex(private val directory: File) {
    private fun file(id: String) = File(directory, key(id) + ".json")
    fun find(id: String?, query: ArtworkQuery): MotionArtwork? {
        val files = if (id != null) listOf(file(id)) else directory.listFiles().orEmpty().filter { it.extension == "json" }
        return files.firstNotNullOfOrNull { path -> runCatching { MotionArtwork.parse(path.readText(), query) }.getOrNull() }
    }
    fun commit(id: String, query: ArtworkQuery, motion: MotionArtwork) {
        directory.mkdirs()
        val body = buildJsonObject {
            put("title", query.title); put("artist", query.artist); put("duration_ms", query.durationMs)
            put("has_motion", true)
            putJsonObject(if (motion.tall) "tall" else "square") { put("m3u8", motion.hls); put("mp4", motion.mp4) }
            putJsonObject("colors") { put("bg", (motion.background and 0xffffff).toString(16).padStart(6, '0')) }
        }.toString()
        val temp = File.createTempFile("motion-", ".partial", directory)
        try {
            temp.writeText(body)
            Files.move(temp.toPath(), file(id).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
    }
    fun remove(id: String): MotionArtwork? {
        val f = file(id)
        val motion = runCatching {
            val body = f.readText(); val root = Json.parseToJsonElement(body).jsonObject
            MotionArtwork.parse(body, ArtworkQuery(root.getValue("title").jsonPrimitive.content,
                root.getValue("artist").jsonPrimitive.content, root.getValue("duration_ms").jsonPrimitive.long))
        }.getOrNull()
        if (f.exists()) check(f.delete()) { "Cannot delete motion manifest" }
        return motion
    }
    fun containsUrl(url: String) = directory.listFiles().orEmpty().any { f ->
        f.extension == "json" && runCatching {
            val root = Json.parseToJsonElement(f.readText()).jsonObject
            listOf("tall", "square").any { listOf("m3u8", "mp4").any { key -> (root[it] as? JsonObject)?.get(key)?.jsonPrimitive?.contentOrNull == url } }
        }.getOrDefault(false)
    }
    fun clear() { directory.listFiles().orEmpty().forEach { check(it.delete()) } }
    companion object {
        fun key(id: String) = MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
