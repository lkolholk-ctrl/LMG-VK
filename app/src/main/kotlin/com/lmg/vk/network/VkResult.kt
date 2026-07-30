package com.lmg.vk.network

/**
 * Восстановлено из `defpackage.C9022e` (success) и `defpackage.C7220e` (error).
 *
 * Результат выполнения [VkMethod]: либо данные, либо (code, message) ошибки VK.
 */
sealed interface VkResult<out T> {

    data class Success<T>(val data: T) : VkResult<T>

    data class Error(val code: Int, val message: String) : VkResult<Nothing>
}

inline fun <T, R> VkResult<T>.map(block: (T) -> R): VkResult<R> = when (this) {
    is VkResult.Success -> VkResult.Success(block(data))
    is VkResult.Error -> this
}

fun <T> VkResult<T>.getOrNull(): T? = (this as? VkResult.Success)?.data
