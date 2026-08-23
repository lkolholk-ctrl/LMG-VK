package com.lmg.vk.ui.navigation

/**
 * Маршруты навигации (батч 15). Каждая нижняя вкладка — это ВЛОЖЕННЫЙ граф со
 * своим стартовым экраном и своими экранами-деталями. За счёт вложенных графов
 * получаем ПЕР-ТАБ бэкстек: переключение вкладки сохраняет её стек и позицию, а
 * не откатывает на старт (цель #81).
 *
 * Общие детали (альбом/артист/плейлист) регистрируются ВНУТРИ каждого графа,
 * где они достижимы, поэтому маршрут префиксуется именем вкладки — иначе одни и
 * те же route столкнутся между графами. Хелперы ниже собирают такие route.
 */
object NavRoutes {

    // ── Графы вкладок (route самого графа) ──
    const val GRAPH_WAVE = "graph_wave"
    const val GRAPH_LIBRARY = "graph_library"
    const val GRAPH_NEW = "graph_new"
    const val GRAPH_SETTINGS = "graph_settings"

    // ── Стартовые экраны вкладок ──
    const val WAVE_HOME = "wave/home"
    const val LIBRARY_HOME = "library/home"
    const val NEW_HOME = "new/home"
    const val SETTINGS_HOME = "settings/home"

    // ── Экран поиска (достижим только с Волны) ──
    const val WAVE_SEARCH = "wave/search"

    /** Лента сниппетов VK (`audio.getSnippets`) — открывается из вкладки New. */
    const val NEW_SNIPPETS = "new/snippets"

    // ── Аргументы ──
    const val ARG_ID = "id"
    const val ARG_NAME = "name"
    const val ARG_KIND = "kind"

    /** Роут детали альбома внутри вкладки [tab] (tab = "wave"/"library"/"new"). */
    fun albumRoute(tab: String) = "$tab/album/{$ARG_ID}"
    fun album(tab: String, id: String) = "$tab/album/$id"

    fun artistRoute(tab: String) = "$tab/artist/{$ARG_ID}"
    fun artist(tab: String, id: String) = "$tab/artist/$id"

    fun playlistRoute(tab: String) = "$tab/playlist/{$ARG_ID}"
    fun playlist(tab: String, id: String) = "$tab/playlist/$id"

    // ── Итоги года (открывается из статистики) ──
    const val YEAR_RECAP = "library/year-recap"

    // ── Аудиозаписи владельца по ссылке (vk.com/audios123, короткое имя) ──
    // Живёт в графе Библиотеки: это чужая фонотека, ближайшая по смыслу к своей.
    // owner_id бывает отрицательным (сообщество), а минус в пути навигация примет
    // только как часть строкового аргумента — поэтому тип строковый, а не Long.
    const val OWNER_AUDIO_ROUTE = "library/owner-audio/{$ARG_ID}"
    fun ownerAudio(ownerId: Long) = "library/owner-audio/$ownerId"
    fun ownerAudioRoute(tab: String) = "$tab/owner-audio/{$ARG_ID}"
    fun ownerAudio(tab: String, ownerId: Long) = "$tab/owner-audio/$ownerId"

    // ── Экран сообщества (vk.com/club<id>, vk.com/public<id>, короткое имя) ──
    // Живёт в графе Библиотеки по той же причине, что и OWNER_AUDIO: экран один
    // на приложение, и регистрировать его в каждом графе значило бы плодить
    // копии состояния. Аргумент строковый: в пути лежит ОТРИЦАТЕЛЬНЫЙ owner_id,
    // а минус NavType.LongType не сматчил бы.
    const val GROUP_ROUTE = "library/group/{$ARG_ID}"
    fun group(ownerId: Long) = "library/group/$ownerId"

    // ── Публичный профиль пользователя VK ──
    const val USER_PROFILE_ROUTE = "library/user/{$ARG_ID}"
    fun userProfile(userId: Long) = "library/user/$userId"
    const val USER_PROFILE_DETAILS_ROUTE = "library/user/{$ARG_ID}/details"
    fun userProfileDetails(userId: Long) = "library/user/$userId/details"
    const val USER_CONNECTIONS_ROUTE = "library/user/{$ARG_ID}/connections/{$ARG_KIND}"
    fun userConnections(userId: Long, kind: String) = "library/user/$userId/connections/$kind"

    // ── Онбординг рекомендаций (открывается из Настроек) ──
    const val RECOMMENDATIONS_ONBOARDING = "settings/recommendations-onboarding"

    // ── Отладочный лог (открывается из Настроек) ──
    // Диагностика воспроизведения на телефоне, где нет adb logcat.
    const val DEBUG_LOG = "settings/debug-log"

    // ── Экран «Загрузки» (скачанное на устройство; вход из Библиотеки) ──
    const val DOWNLOADS = "library/downloads"
    const val VK_HISTORY = "library/vk-history"

    // ── Локальная медиатека (только внутри Библиотеки) ──
    const val LOCAL_LIBRARY = "library/local"
    const val LOCAL_ARTIST_ROUTE = "library/local/artist/{$ARG_NAME}"
    fun localArtist(name: String) = "library/local/artist/$name"
    const val LOCAL_ALBUM_ROUTE = "library/local/album/{$ARG_ID}/{$ARG_NAME}"
    fun localAlbum(id: Long, name: String) = "library/local/album/$id/$name"

    /** Короткий тег вкладки для построения префиксных route. */
    const val TAB_WAVE = "wave"
    const val TAB_LIBRARY = "library"
    const val TAB_NEW = "new"

    /** По любому текущему route определить, какому графу-вкладке он принадлежит —
     *  для подсветки бара и «вкладочной» логики (дым Волны, мини-плеер). */
    fun graphOf(route: String?): String = when {
        route == null -> GRAPH_WAVE
        route.startsWith("wave") -> GRAPH_WAVE
        route.startsWith("library") -> GRAPH_LIBRARY
        route.startsWith("new") -> GRAPH_NEW
        route.startsWith("settings") -> GRAPH_SETTINGS
        else -> GRAPH_WAVE
    }

    /** Является ли текущий route стартовым экраном своей вкладки (деталей нет
     *  сверху) — нужно, чтобы понимать, показывать ли тяжёлый дым Волны. */
    fun isTabHome(route: String?): Boolean =
        route == WAVE_HOME || route == LIBRARY_HOME || route == NEW_HOME || route == SETTINGS_HOME
}
