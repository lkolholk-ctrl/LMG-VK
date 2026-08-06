package com.lmg.vk.network.dto.gen.music

import com.squareup.moshi.JsonClass

/**
 * Издатель аудиокниги — `AudioBooksPublisherDto` (`C4964e.java:9-11`,
 * типы `C7715e.java:3-5`, `:54`): ключи `id` (Int) и `name` (String).
 *
 * Раньше поле `AudioBook.publisher` было типизировано как [Link], а это
 * `AudioStreamMix.Link` с ключами `id: String` / `title: String`. Ключа
 * `title` в ответе нет вовсе, поэтому имя издателя всегда приходило пустым.
 */
@JsonClass(generateAdapter = true)
data class AudioBookPublisher(
    val id: Int = 0,
    val name: String? = null,
)
