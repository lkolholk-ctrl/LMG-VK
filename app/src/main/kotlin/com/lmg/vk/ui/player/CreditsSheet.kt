package com.lmg.vk.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.engine.Track
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.lmg.LmgSyncApi

/**
 * Кто сделал трек: авторы, продюсеры, лейбл, год.
 *
 * ИСТОЧНИК — САМ VK. `audio.getLyrics` возвращает рядом с текстом поле
 * `credits` (восстановлено из адаптеров APK, см. `AudioLyricsContainer`), и это
 * ровно то, что официальный клиент показывает в разделе «Авторы». Раньше экран
 * спрашивал данные у [LmgSyncApi.fetchCredits], который в этой сборке всегда
 * отдаёт `null` — поэтому пункт и был пустым ВСЕГДА, независимо от трека.
 *
 * ФОРМАТ НЕ ПАРСИМ ПО ШАБЛОНУ. В доках его нет: у части записей это одна
 * строка, у части — несколько через перевод строки, вида «Автор музыки: …».
 * Поэтому делим только по переводам строк и, если в строке есть двоеточие,
 * показываем «роль → имя» двумя колонками. Всё остальное выводим как есть:
 * выдумывать структуру, которой не видел, значит приписывать музыке чужих людей.
 */
@Composable
fun CreditsContent(track: Track, durationMs: Long) {
    var vkCredits by remember(track.id) { mutableStateOf<String?>(null) }
    var extra by remember(track.id) { mutableStateOf<LmgSyncApi.TrackCredits?>(null) }
    var loading by remember(track.id) { mutableStateOf(true) }

    LaunchedEffect(track.id) {
        loading = true
        vkCredits = MusicBackend.getTrackCredits(track.id)
        // Внешняя база — дополнение, а не замена: в этой сборке она отключена
        // (fetchCredits возвращает null), но когда появится, даст роли отдельными
        // полями. VK-строка при этом остаётся главной.
        extra = LmgSyncApi.fetchCredits(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            durationMs = durationMs,
        )
        loading = false
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = track.title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = track.artist,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 2.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        val fromVk = vkCredits
        val fromDb = extra
        when {
            loading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.7f),
                        strokeWidth = 2.dp,
                        modifier = Modifier.width(18.dp).height(18.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Загружаем авторов…",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                    )
                }
            }

            !fromVk.isNullOrBlank() -> {
                fromVk.split('\n')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .forEach { line -> CreditsLine(line) }
            }

            fromDb != null && fromDb.found -> {
                fromDb.people.forEach { person ->
                    CreditsRow(name = person.name, role = person.role)
                }
                if (fromDb.label.isNotBlank() || fromDb.year.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = listOf(fromDb.label, fromDb.year)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                    )
                }
            }

            else -> {
                Text(
                    text = "VK не указал авторов для этой записи.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                )
                Text(
                    // Объясняем ПРИЧИНУ, а не просто «нет данных»: кредиты
                    // приходят вместе с текстом песни, поэтому у треков без
                    // текста их не бывает вовсе — это не сбой приложения.
                    text = "Авторы приходят вместе с текстом песни, поэтому у записей " +
                        "без текста их обычно нет.",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/**
 * Строка кредитов из VK. «Роль: Имя» разводится по краям, как в списке; строка
 * без двоеточия выводится целиком.
 *
 * Делим по ПЕРВОМУ двоеточию: в именах и названиях лейблов оно встречается
 * («Sony: Legacy»), и `split` без ограничения разорвал бы их на части.
 */
@Composable
private fun CreditsLine(line: String) {
    val idx = line.indexOf(':')
    if (idx <= 0 || idx == line.lastIndex) {
        Text(
            text = line,
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
        return
    }
    CreditsRow(
        name = line.substring(idx + 1).trim(),
        role = line.substring(0, idx).trim(),
    )
}

@Composable
private fun CreditsRow(name: String, role: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = role,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp,
        )
    }
}
