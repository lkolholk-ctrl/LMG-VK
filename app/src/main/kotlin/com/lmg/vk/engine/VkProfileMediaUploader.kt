package com.lmg.vk.engine

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.lmg.vk.network.VkApiLocator
import com.lmg.vk.network.VkResult
import com.lmg.vk.network.applyVkRequestIdentity
import com.lmg.vk.network.methods.VkMethodsRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import org.json.JSONObject

enum class ProfileImageKind { AVATAR, COVER }

/** Official three-step owner image flow: get server -> multipart -> save. */
object VkProfileMediaUploader {
    suspend fun upload(context: Context, uri: Uri, kind: ProfileImageKind): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                validateImage(context, uri, kind)
                val registry = VkMethodsRegistry(VkApiLocator.apiClient())
                val server = when (kind) {
                    ProfileImageKind.AVATAR -> registry.photosGetOwnerPhotoUploadServer()
                    ProfileImageKind.COVER -> registry.photosGetOwnerCoverUploadServer()
                }.requireUploadData()
                val rawResponse = uploadMultipart(
                    context = context,
                    uri = uri,
                    uploadUrl = server.uploadUrl.ifBlank {
                        server.fallbackUploadUrl ?: error("VK returned an empty upload URL")
                    },
                )
                if (kind == ProfileImageKind.AVATAR) {
                    check(JSONObject(rawResponse).has("hash")) {
                        "VK returned an invalid profile photo upload response"
                    }
                }
                when (kind) {
                    ProfileImageKind.AVATAR -> registry.photosSaveOwnerPhoto(rawResponse).requireUploadData()
                    ProfileImageKind.COVER -> registry.photosSaveOwnerCover(rawResponse).requireUploadData()
                }
                Unit
            }
        }

    private fun validateImage(context: Context, uri: Uri, kind: ProfileImageKind) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Couldn't read the selected image")
        input.use { BitmapFactory.decodeStream(it, null, options) }
        val width = options.outWidth
        val height = options.outHeight
        require(width > 0 && height > 0) { "Unsupported image format" }
        require(width <= 7_000 && height <= 7_000) { "Image must not exceed 7000 x 7000" }
        if (kind == ProfileImageKind.COVER) {
            require(context.contentResolver.getType(uri) != "image/gif") {
                "GIF covers are not supported by VK"
            }
            require(width >= 960 && height >= 384) { "Cover must be at least 960 x 384" }
            val ratio = width.toFloat() / height
            require(ratio in 2.35f..2.65f) { "Prepare the cover close to the VK 2.5:1 format" }
        }
    }

    private fun uploadMultipart(context: Context, uri: Uri, uploadUrl: String): String {
        val boundary = "----LmgVk${UUID.randomUUID()}"
        val mime = context.contentResolver.getType(uri)?.takeIf { it.startsWith("image/") }
            ?: "image/jpeg"
        val extension = mime.substringAfter('/', "jpg").substringBefore('+')
        val connection = (URL(uploadUrl).openConnection() as HttpURLConnection).applyVkRequestIdentity().apply {
            requestMethod = "POST"
            doOutput = true
            useCaches = false
            connectTimeout = 30_000
            readTimeout = 60_000
            setChunkedStreamingMode(64 * 1024)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        try {
            BufferedOutputStream(connection.outputStream).use { output ->
                output.write("--$boundary\r\n".toByteArray())
                output.write(
                    "Content-Disposition: form-data; name=\"photo\"; filename=\"profile.$extension\"\r\n"
                        .toByteArray(),
                )
                output.write("Content-Type: $mime\r\n\r\n".toByteArray())
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                } ?: error("Couldn't reopen the selected image")
                output.write("\r\n--$boundary--\r\n".toByteArray())
            }
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            check(code in 200..299) { "VK upload server returned HTTP $code" }
            check(response.isNotBlank()) { "VK upload server returned an empty response" }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun <T> VkResult<T>.requireUploadData(): T = when (this) {
        is VkResult.Success -> data
        is VkResult.Error -> error(message.ifBlank { "VK error $code" })
    }
}
