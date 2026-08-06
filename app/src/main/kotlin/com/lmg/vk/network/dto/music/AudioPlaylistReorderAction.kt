package com.lmg.vk.network.dto.music

/**
 * Один элемент параметра `actions` метода `audio.reorderInPlaylist`.
 *
 * На проводе это **не объект, а позиционный массив** из трёх значений:
 * `[trackOwnerId, trackId, newIndex]`. Порядок восстановлен из
 * `C3294e.java:43-55` (сначала `C1591e.vip`, затем `.ad`, затем `.metrica`),
 * имена полей — из `C1591e.java:72-84`. `newIndex = -1` — дефолт
 * двухаргументного конструктора оригинала (`C1591e.java:27-31`).
 *
 * Ключей у элементов нет, поэтому Moshi здесь не применим — сериализация
 * ручная, см. [encode].
 */
data class AudioPlaylistReorderAction(
    val trackOwnerId: Long,
    val trackId: Int,
    val newIndex: Int = -1,
) {
    companion object {
        /** `[[ownerId,trackId,index],…]` — ровно та форма, что ждёт VK. */
        fun encode(actions: List<AudioPlaylistReorderAction>): String =
            actions.joinToString(separator = ",", prefix = "[", postfix = "]") {
                "[${it.trackOwnerId},${it.trackId},${it.newIndex}]"
            }
    }
}
