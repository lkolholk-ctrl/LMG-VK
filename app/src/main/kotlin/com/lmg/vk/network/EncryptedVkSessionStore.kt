package com.lmg.vk.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Постоянное хранилище VK-сессии. Токены никогда не записываются в preferences
 * открытым текстом: JSON шифруется AES/GCM ключом из Android Keystore.
 */
class EncryptedVkSessionStore(context: Context) : VkMultiSessionStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()

    @Volatile
    private var cached: VkAuthSession = VkAuthSession.EMPTY

    @Volatile
    private var cachedSessions: List<VkAuthSession> = emptyList()

    init {
        val restored = readState()
        cached = restored.active
        cachedSessions = restored.sessions
    }

    override val sessions: List<VkAuthSession>
        get() = cachedSessions

    override var session: VkAuthSession
        get() = cached
        set(value) = synchronized(lock) {
            val updated = cachedSessions.toMutableList()
            if (value == VkAuthSession.EMPTY) {
                updated.removeAll { sameAccount(it, cached) }
                cached = updated.firstOrNull() ?: VkAuthSession.EMPTY
            } else {
                val index = updated.indexOfFirst { sameAccount(it, value) }
                if (index >= 0) {
                    updated[index] = value
                } else {
                    updated.add(value)
                }
                cached = value
            }
            cachedSessions = updated.toList()
            persist()
        }

    override fun activate(userId: Long): VkAuthSession? = synchronized(lock) {
        val next = cachedSessions.firstOrNull { it.userId == userId && it.accessToken.isNotBlank() }
            ?: return@synchronized null
        cached = next
        persist()
        next
    }

    override fun remove(userId: Long): VkAuthSession = synchronized(lock) {
        val updated = cachedSessions.filterNot { it.userId == userId }
        cachedSessions = updated
        if (cached.userId == userId) cached = updated.firstOrNull() ?: VkAuthSession.EMPTY
        persist()
        cached
    }

    private fun readState(): RestoredState {
        val payload = preferences.getString(KEY_PAYLOAD, null)
            ?: return RestoredState(VkAuthSession.EMPTY, emptyList())
        return runCatching {
            val decrypted = decrypt(payload)
            val stored = runCatching { json.decodeFromString<StoredAccounts>(decrypted) }
                .getOrElse {
                    val legacy = json.decodeFromString<VkAuthSession>(decrypted)
                    StoredAccounts(legacy.userId, listOf(legacy).filter { it.accessToken.isNotBlank() })
                }
            val valid = stored.sessions.filter { it.accessToken.isNotBlank() }
            val active = valid.firstOrNull { it.userId == stored.activeUserId }
                ?: valid.firstOrNull()
                ?: VkAuthSession.EMPTY
            RestoredState(active, valid)
        }.getOrElse {
            preferences.edit().remove(KEY_PAYLOAD).commit()
            RestoredState(VkAuthSession.EMPTY, emptyList())
        }
    }

    private fun persist() {
        if (cachedSessions.isEmpty()) {
            check(preferences.edit().remove(KEY_PAYLOAD).commit()) { "Unable to clear VK sessions" }
            return
        }
        val encrypted = encrypt(
            json.encodeToString(StoredAccounts(cached.userId, cachedSessions)),
        )
        check(preferences.edit().putString(KEY_PAYLOAD, encrypted).commit()) {
            "Unable to persist VK sessions"
        }
    }

    private fun sameAccount(left: VkAuthSession, right: VkAuthSession): Boolean = when {
        left.userId != 0L && right.userId != 0L -> left.userId == right.userId
        left.username.isNotBlank() && right.username.isNotBlank() -> left.username == right.username
        else -> left.accessToken.isNotBlank() && left.accessToken == right.accessToken
    }

    @Serializable
    private data class StoredAccounts(
        val activeUserId: Long,
        val sessions: List<VkAuthSession>,
    )

    private data class RestoredState(
        val active: VkAuthSession,
        val sessions: List<VkAuthSession>,
    )

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encrypted = Base64.encodeToString(
            cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP,
        )
        return "$iv.$encrypted"
    }

    private fun decrypt(payload: String): String {
        val separator = payload.indexOf('.')
        require(separator > 0 && separator < payload.lastIndex) { "Invalid VK session payload" }
        val iv = Base64.decode(payload.substring(0, separator), Base64.NO_WRAP)
        val encrypted = Base64.decode(payload.substring(separator + 1), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "lmg_vk_session_v1"
        const val KEY_PAYLOAD = "encrypted_session"
        const val KEY_ALIAS = "lmg_vk_session_key_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
