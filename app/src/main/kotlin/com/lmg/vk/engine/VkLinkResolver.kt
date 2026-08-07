package com.lmg.vk.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.toTrack
import com.lmg.vk.network.VkApiLocator
import com.lmg.vk.network.VkResult
import com.lmg.vk.network.methods.VkAudioApi
import com.lmg.vk.network.methods.VkMethodsRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Обработка входящих ссылок ВКонтакте — порт роутера VK X (`C5658e`, статический
 * блок со списком `Regex → handler`; обработчики — `C0139e`).
 *
 * Почему отдельный объект, а не логика в `MainActivity`: ссылка приходит и на
 * холодном старте, и в `onNewIntent` уже живой Activity, а навигация живёт в
 * Compose-графе внутри [com.lmg.vk.ui.AppRoot]. Разбор + сетевой резолв поэтому
 * вынесены сюда, а результат уезжает в [VkLinkRouter], который слушает UI.
 *
 * Отступления от VK X помечены в комментариях `ОТСТУПЛЕНИЕ`.
 */
object VkLinkResolver {

    /**
     * Хосты, которые считаем «своими». `vk.ru` — второй официальный домен (см.
     * [com.lmg.vk.network.VkApiClient.probeAndSelectApiDomain]); `m.` — мобильная
     * версия, ссылки из мессенджеров часто приходят именно с неё.
     */
    private val VK_HOSTS = setOf(
        "vk.com", "m.vk.com",
        "vk.ru", "m.vk.ru",
        // Старый домен всё ещё редиректит и встречается в переписках.
        "vkontakte.ru", "m.vkontakte.ru",
    )

    // ─────────────────────── regex-таблица (VK X C5658e) ───────────────────────
    // Формы дословно с `C5658e.java:15-28`, ужесточены только числовые группы:
    // у VK X owner описан как `([-0-9]+)`, что пропускает мусор вида `--1-2`.

    /** `/audio_playlist-2000123_456_ab12cd` — плейлист/альбом (VK X: `openPlaylist`). */
    private val AUDIO_PLAYLIST = Regex("""^/audio_playlist(-?\d+)_(\d+)(?:[_/]([0-9a-zA-Z]+))?""")

    /** `/audio-2000123_456_ab12cd` — одиночный трек (VK X: `openTrack`). */
    private val AUDIO_TRACK = Regex("""^/audio(-?\d+)_(\d+)(?:[_/]([0-9a-zA-Z]+))?""")

    /** `/music/album/-2000123_456_ab12cd`. */
    private val MUSIC_ALBUM = Regex("""^/music/album/(-?\d+)_(\d+)_?([0-9a-zA-Z]*)""")

    /** `/music/playlist/-2000123_456_ab12cd`. */
    private val MUSIC_PLAYLIST = Regex("""^/music/playlist/(-?\d+)_(\d+)_?([0-9a-zA-Z]*)""")

    /** `/artist/slug` и `/music/artist/slug` — у VK X это две отдельные строки. */
    private val ARTIST = Regex("""^/(?:music/)?artist/([-_a-zA-Z0-9]+)""")

    /** `/audios-2000123` — аудиозаписи владельца (VK X: `openUserAudios`). */
    private val OWNER_AUDIOS = Regex("""^/audios(-?\d+)""")

    /**
     * Короткая ссылка на экранное имя: `vk.com/somename`. Один сегмент пути,
     * начинается с буквы — так VK ограничивает screen_name. Числовые формы
     * (`id123`, `club123`) разбираются локально и сюда не доходят.
     */
    private val SCREEN_NAME = Regex("""^/([a-zA-Z][a-zA-Z0-9._]{1,62})$""")

    /** `id123` / `club123` / `public123` / `event123` — id виден прямо в адресе. */
    private val NUMERIC_USER = Regex("""^/id(\d+)$""")
    private val NUMERIC_GROUP = Regex("""^/(?:club|public|event)(\d+)$""")

    /**
     * Ключ доступа к приватному аудио/плейлисту. VK выдаёт строчный hex, а у VK X
     * группа описана как `[0-9a-zA-Z]+` — на реальных `z=`-ссылках это ловило
     * хвост контекста (`audio_playlist-2000_1/audios-2000` → ключ «audios»).
     * ОТСТУПЛЕНИЕ: ключом считаем только hex достаточной длины, иначе игнорируем.
     */
    private val ACCESS_KEY = Regex("""^[0-9a-f]{6,}$""")

    // ───────────────────────────── публичный API ─────────────────────────────

    /** Ссылка вообще про ВКонтакте? Схему не сужаем: приходит и http, и https. */
    fun isVkLink(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase()
        if (scheme != null && scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return false
        return host in VK_HOSTS
    }

    /**
     * Разбор без обращения к сети. `null` — по самой ссылке цель не определить
     * (либо она не музыкальная, либо нужен `utils.resolveScreenName`).
     */
    fun parseOffline(uri: Uri): VkLinkTarget? {
        // 1. Параметр `z=` — веб-плеер VK кладёт настоящую цель именно в него, а
        //    путь при этом любой (`/music?z=audio_playlist...`, `/?z=audio-1_2`).
        //    Проверяется ПЕРВЫМ и независимо от пути: у ссылки может вообще не
        //    быть значимого пути, кроме «/».
        //    У VK X такие ссылки не работали: его роутер режет строку по «?» ДО
        //    матчинга (`C5658e.java:86`), поэтому паттерны с `\?z=` были мертвы.
        //    ОТСТУПЛЕНИЕ: разбираем `z=` явно — иначе теряется самый частый
        //    формат «поделиться» из браузерной версии.
        runCatching { uri.getQueryParameter("z") }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { z -> parsePath("/" + z.trimStart('/').lowercase()) }
            ?.let { return it }

        // 2. Обычные пути.
        val path = uri.path?.takeIf { it.isNotBlank() }?.lowercase() ?: return null
        return parsePath(path)
    }

    /**
     * Полный резолв: разбор + при необходимости `utils.resolveScreenName`.
     * Ошибку не проглатываем — вызывающий обязан показать её пользователю.
     */
    suspend fun resolve(uri: Uri): VkLinkResolution {
        if (!isVkLink(uri)) return VkLinkResolution.NotVkLink

        parseOffline(uri)?.let { return VkLinkResolution.Resolved(it) }

        val path = uri.path?.lowercase().orEmpty()

        // id123 / club123 — владелец известен без запроса.
        NUMERIC_USER.find(path)?.let { m ->
            val id = m.groupValues[1].toLongOrNull() ?: return VkLinkResolution.Unsupported(path)
            return VkLinkResolution.Resolved(VkLinkTarget.OwnerAudio(id, isGroup = false))
        }
        NUMERIC_GROUP.find(path)?.let { m ->
            val id = m.groupValues[1].toLongOrNull() ?: return VkLinkResolution.Unsupported(path)
            // Музыкальные методы VK ждут отрицательный owner_id сообщества
            // (та же логика, что в `VkGroup.audioOwnerId`).
            // wantsProfile: ссылка вида /club123 ведёт на СООБЩЕСТВО, поэтому
            // открываем его экран, а не сразу список треков.
            return VkLinkResolution.Resolved(
                VkLinkTarget.OwnerAudio(-id, isGroup = true, wantsProfile = true)
            )
        }

        // Короткое имя: кто за ним стоит, знает только сервер.
        SCREEN_NAME.find(path)?.let { m ->
            return resolveScreenName(m.groupValues[1])
        }

        return VkLinkResolution.Unsupported(path)
    }

    /**
     * `utils.resolveScreenName` (спека `docs/vkx-port/04-periphery.md` §1).
     *
     * Метод уже есть в реестре, но объявлен как `execute<Any>`, поэтому ответ
     * приходит Moshi-картой. Типизированное DTO `ResolvedScreenName` из спеки
     * требует правки `VkMethodsRegistry` — это чужая зона, поэтому разбираем
     * карту здесь, по тем же JSON-именам (`object_id`, `type`).
     */
    private suspend fun resolveScreenName(screenName: String): VkLinkResolution {
        val client = runCatching { VkApiLocator.apiClient() }.getOrNull()
            ?: return VkLinkResolution.Failed("Сетевое ядро VK ещё не готово")

        val response = runCatching { VkMethodsRegistry(client).resolveScreenName(screenName) }
            .getOrElse { return VkLinkResolution.Failed(it.message ?: "Сбой запроса к VK") }

        val payload = when (response) {
            is VkResult.Error -> return VkLinkResolution.Failed(
                response.message.ifBlank { "VK вернул ошибку ${response.code}" },
            )
            is VkResult.Success -> response.data
        }

        // Неизвестное имя VK отдаёт пустым массивом/объектом, а не ошибкой.
        val fields = payload as? Map<*, *>
            ?: return VkLinkResolution.Unsupported("vk.com/$screenName")
        // Moshi без типа парсит числа как Double — приводим через Number.
        val objectId = (fields["object_id"] as? Number)?.toLong()
            ?: return VkLinkResolution.Unsupported("vk.com/$screenName")
        val type = (fields["type"] as? String)?.lowercase()

        return when (type) {
            "user" -> VkLinkResolution.Resolved(
                VkLinkTarget.OwnerAudio(objectId, isGroup = false),
            )
            // Набор значений `type` в спеке не подтверждён, поэтому группой
            // считаем все известные «сообществные» варианты.
            "group", "page", "event", "club" -> VkLinkResolution.Resolved(
                // Короткое имя сообщества (`vk.com/somename`) — тоже ссылка на
                // само сообщество, а не на его аудио.
                VkLinkTarget.OwnerAudio(-objectId, isGroup = true, wantsProfile = true),
            )
            else -> VkLinkResolution.Unsupported("vk.com/$screenName")
        }
    }

    /**
     * Точка входа для `MainActivity`: разобрать ссылку и что-то с ней сделать.
     * Экранные цели уходят в [VkLinkRouter], трек играем сразу, нераспознанное —
     * честно наружу (браузер) с объяснением, а не молчаливое ничего.
     */
    suspend fun handle(context: Context, uri: Uri) {
        when (val resolution = resolve(uri)) {
            is VkLinkResolution.Resolved -> when (val target = resolution.target) {
                is VkLinkTarget.Audio -> {
                    val failure = playAudio(context, target)
                    if (failure != null) reportAndOpenOutside(context, uri, failure)
                }
                // Аудиозаписи владельца теперь открываются в приложении: у экрана
                // появился свой маршрут (NavRoutes.OWNER_AUDIO_ROUTE), поэтому цель
                // уходит в роутер вместе с остальными экранными, а не в браузер.
                else -> VkLinkRouter.post(target)
            }

            is VkLinkResolution.Unsupported -> reportAndOpenOutside(
                context,
                uri,
                // Формулировка нейтральная: под Unsupported попадают и совсем не
                // музыкальные ссылки, и музыкальные без конкретной цели
                // (сам раздел /music, аудиокниги, подкасты) — обещать про них
                // «не музыкальная» было бы неправдой.
                "Эту ссылку VK приложение не открывает",
            )

            is VkLinkResolution.Failed -> reportAndOpenOutside(context, uri, resolution.reason)

            VkLinkResolution.NotVkLink -> openOutside(context, uri)
        }
    }

    /**
     * Одиночный трек: резолвим через `audio.getById` и играем той же дорогой, что
     * и остальные экраны (кэш [MusicBackend] → [PlayerController]).
     * Возвращает текст проблемы или `null` при успехе.
     */
    private suspend fun playAudio(context: Context, target: VkLinkTarget.Audio): String? {
        val client = runCatching { VkApiLocator.apiClient() }.getOrNull()
            ?: return "Сетевое ядро VK ещё не готово"

        val result = runCatching { VkAudioApi(client).getById(listOf(target.apiId)) }
            .getOrElse { return it.message ?: "Сбой запроса к VK" }

        val audios = when (result) {
            is VkResult.Error -> return result.message.ifBlank { "VK вернул ошибку ${result.code}" }
            is VkResult.Success -> result.data
        }
        if (audios.isEmpty()) return "VK не отдал этот трек"

        // adoptTracks кладёт треки в кэш бэкенда — без него плеер полез бы за
        // повторным audio.getById уже из StreamingDataSource.
        val playable = MusicBackend.adoptTracks(audios)
            .map { it.toTrack() }
            .filter { it.isAvailable }
        if (playable.isEmpty()) return "Аудиозапись недоступна"

        withContext(Dispatchers.Main) {
            PlayerController.play(context, playable, 0)
            // Тот же канал, что у тапа по уведомлению: AppRoot раскроет плеер.
            NotificationRouter.emitOpenLargePlayer()
        }
        return null
    }

    // ───────────────────────────── запасной путь ─────────────────────────────

    /** Сообщить причину и всё-таки открыть ссылку — в браузере. */
    private suspend fun reportAndOpenOutside(context: Context, uri: Uri, reason: String) {
        toast(context, reason)
        openOutside(context, uri)
    }

    /**
     * Открыть ссылку вне приложения. Обычный `ACTION_VIEW` вернулся бы к нам же
     * (мы сами в обработчиках), поэтому свой пакет из кандидатов исключаем и
     * стартуем выбранного явно. Видимость браузеров на Android 11+ обеспечена
     * блоком `<queries>` в манифесте.
     *
     * suspend, потому что вызывается с IO (разбор ссылки сетевой), а Toast и
     * startActivity должны уходить с главного потока.
     */
    suspend fun openOutside(context: Context, uri: Uri) {
        val view = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        val external = runCatching {
            context.packageManager
                .queryIntentActivities(view, 0)
                .firstOrNull { it.activityInfo?.packageName != context.packageName }
                ?.activityInfo
                ?.packageName
        }.getOrNull()

        if (external == null) {
            toast(context, "Открыть ссылку нечем: браузер не найден")
            return
        }

        val started = withContext(Dispatchers.Main) {
            runCatching {
                context.startActivity(
                    Intent(view).setPackage(external).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.isSuccess
        }
        if (!started) toast(context, "Браузер отказался открыть ссылку")
    }

    private suspend fun toast(context: Context, text: String) {
        withContext(Dispatchers.Main) {
            android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // ─────────────────────────────── внутреннее ───────────────────────────────

    private fun parsePath(path: String): VkLinkTarget? {
        MUSIC_ALBUM.find(path)?.let { return it.toPlaylistTarget(isAlbum = true) }
        MUSIC_PLAYLIST.find(path)?.let { return it.toPlaylistTarget(isAlbum = false) }
        // audio_playlist ДОЛЖЕН проверяться раньше audio: иначе `/audio_playlist…`
        // съедается паттерном трека (у VK X порядок в списке тот же).
        AUDIO_PLAYLIST.find(path)?.let { return it.toPlaylistTarget(isAlbum = false) }
        AUDIO_TRACK.find(path)?.let { match ->
            val (owner, id) = match.groupValues[1] to match.groupValues[2]
            return VkLinkTarget.Audio(
                ownerId = owner.toLongOrNull() ?: return null,
                audioId = id.toLongOrNull() ?: return null,
                accessKey = match.accessKeyOrNull(),
            )
        }
        ARTIST.find(path)?.let { match ->
            // artist_id у VK — это либо число, либо домен артиста; каталог принимает
            // и то и другое (см. MusicBackend.getArtist), поэтому передаём как есть.
            return VkLinkTarget.Artist(match.groupValues[1])
        }
        OWNER_AUDIOS.find(path)?.let { match ->
            val owner = match.groupValues[1].toLongOrNull() ?: return null
            return VkLinkTarget.OwnerAudio(owner, isGroup = owner < 0)
        }
        return null
    }

    private fun MatchResult.toPlaylistTarget(isAlbum: Boolean): VkLinkTarget? {
        val ownerId = groupValues[1].toLongOrNull() ?: return null
        val playlistId = groupValues[2].toIntOrNull() ?: return null
        val accessKey = accessKeyOrNull()
        return if (isAlbum) {
            VkLinkTarget.Album(ownerId, playlistId, accessKey)
        } else {
            VkLinkTarget.Playlist(ownerId, playlistId, accessKey)
        }
    }

    private fun MatchResult.accessKeyOrNull(): String? =
        groupValues.getOrNull(3)
            ?.takeIf { it.isNotBlank() && ACCESS_KEY.matches(it) }
}

/** Куда ведёт разобранная ссылка. */
sealed interface VkLinkTarget {

    /**
     * Альбом (`/music/album/...`). В VK альбом и плейлист — один и тот же
     * audio_playlist, но в LMG VK у них разные экраны, поэтому различаем по URL.
     */
    data class Album(
        val ownerId: Long,
        val playlistId: Int,
        val accessKey: String? = null,
    ) : VkLinkTarget {
        /** Формат id, который ждут экраны и `MusicBackend.parsePlaylistId`. */
        val navId: String get() = "${ownerId}_$playlistId"
    }

    /** Плейлист (`/music/playlist/...`, `/audio_playlist...`, `z=audio_playlist...`). */
    data class Playlist(
        val ownerId: Long,
        val playlistId: Int,
        val accessKey: String? = null,
    ) : VkLinkTarget {
        val navId: String get() = "${ownerId}_$playlistId"
    }

    /** Артист: число или домен — каталог VK принимает оба варианта. */
    data class Artist(val idOrDomain: String) : VkLinkTarget

    /** Одиночный трек (`/audio-2000_123`). */
    data class Audio(
        val ownerId: Long,
        val audioId: Long,
        val accessKey: String? = null,
    ) : VkLinkTarget {
        /** `audio.getById` принимает третий сегмент с ключом доступа. */
        val apiId: String
            get() = listOfNotNull("${ownerId}_$audioId", accessKey).joinToString("_")
    }

    /** Аудиозаписи пользователя/сообщества (`/audios123`, короткое имя). */
    /**
     * Аудиозаписи владельца (`/audios-123`, `/audios123`).
     *
     * [isGroup] — владелец это сообщество (отрицательный owner_id).
     * [wantsProfile] — ссылка вела на само сообщество (`/club123`), а не на его
     * аудио: тогда правильнее открыть экран сообщества, а не сразу список
     * треков. Различие смысловое: `/audios-123` пользователь открывал ради
     * музыки, `/club123` — ради сообщества.
     */
    data class OwnerAudio(
        val ownerId: Long,
        val isGroup: Boolean,
        val wantsProfile: Boolean = false,
    ) : VkLinkTarget
}

/** Результат разбора ссылки. */
sealed interface VkLinkResolution {

    data class Resolved(val target: VkLinkTarget) : VkLinkResolution

    /** Ссылка VK, но не про музыку — либо мы такое ещё не открываем. */
    data class Unsupported(val what: String) : VkLinkResolution

    /** Разобрать не смогли из-за сети/ответа VK; [reason] показываем пользователю. */
    data class Failed(val reason: String) : VkLinkResolution

    data object NotVkLink : VkLinkResolution
}

/**
 * Мост «ссылка → навигация». Навигация живёт в Compose (AppRoot держит
 * NavController), а ссылка приходит в Activity, иногда ДО первой композиции —
 * поэтому не событийная шина, а состояние: цель дождётся подписчика на холодном
 * старте. [consume] обнуляет её после обработки, чтобы пересоздание UI не увело
 * пользователя по старой ссылке во второй раз.
 */
object VkLinkRouter {
    private val _pending = MutableStateFlow<VkLinkTarget?>(null)
    val pending: StateFlow<VkLinkTarget?> = _pending.asStateFlow()

    fun post(target: VkLinkTarget) {
        _pending.value = target
    }

    fun consume() {
        _pending.value = null
    }
}
