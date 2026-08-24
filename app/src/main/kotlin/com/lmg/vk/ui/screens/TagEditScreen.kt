package com.lmg.vk.ui.screens

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.R
import com.lmg.vk.data.local.LocalLibraryStore
import com.lmg.vk.engine.TagEditor
import com.lmg.vk.engine.Track
import com.lmg.vk.ui.theme.LiquidColors
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.theme.VkSansDisplay
import com.lmg.vk.ui.theme.VkSansText
import kotlinx.coroutines.launch

@Composable
fun TagEditScreen(track: Track, onBack: () -> Unit) {
    val lc = LiquidTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    val name = remember(track.id) { TagEditor.displayName(context, track.uri) }
    val supported = remember(name) { TagEditor.isSupported(name) }

    var loading by remember { mutableStateOf(supported) }
    var title by remember { mutableStateOf(track.title) }
    var artist by remember { mutableStateOf(track.artist) }
    var album by remember { mutableStateOf(track.albumName) }
    var albumArtist by remember { mutableStateOf("") }
    var trackNo by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }

    var status by remember { mutableStateOf("") }      // сообщение под кнопкой
    var saving by remember { mutableStateOf(false) }

    fun currentTags() = TagEditor.Tags(title.trim(), artist.trim(), album.trim(),
        albumArtist.trim(), trackNo.trim(), year.trim(), genre.trim())

    // Читаем теги из файла (фон), заполняем поля.
    LaunchedEffect(track.id, supported) {
        if (!supported) { loading = false; return@LaunchedEffect }
        val t = TagEditor.read(context, track.uri, name)
        if (t != null) {
            if (t.title.isNotBlank()) title = t.title
            if (t.artist.isNotBlank()) artist = t.artist
            if (t.album.isNotBlank()) album = t.album
            albumArtist = t.albumArtist
            trackNo = t.trackNumber
            year = t.year
            genre = t.genre
        }
        loading = false
    }

    suspend fun performWrite() {
        saving = true; status = context.getString(R.string.status_saving)
        when (val r = TagEditor.write(context, track.uri, name, currentTags())) {
            is TagEditor.WriteResult.Ok -> {
                runCatching {
                    LocalLibraryStore.updateTags(
                        context, track.id, title.trim(), artist.trim(), album.trim(),
                        trackNo.trim().toIntOrNull() ?: 0, year.trim().toIntOrNull() ?: 0
                    )
                }
                saving = false; status = context.getString(R.string.status_saved)
            }
            is TagEditor.WriteResult.Unsupported -> { saving = false; status = context.getString(R.string.status_format_unsupported) }
            is TagEditor.WriteResult.Error -> { saving = false; status = r.message }
            is TagEditor.WriteResult.NeedsPermission -> { /* API 29 — обработается лаунчером ниже */ }
        }
    }

    // Лаунчер запроса разрешения на запись файла (scoped storage).
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch { performWrite() }
        } else {
            saving = false; status = context.getString(R.string.status_access_denied)
        }
    }

    fun onSaveClick() {
        if (saving) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val isender = TagEditor.createWriteRequest(context, listOf(track.uri))
            if (isender != null) {
                saving = true; status = context.getString(R.string.status_requesting_permission)
                permLauncher.launch(IntentSenderRequest.Builder(isender).build())
                return
            }
        }
        // API 29: пробуем записать; если нужно разрешение — запустим intentSender из результата.
        scope.launch {
            saving = true; status = context.getString(R.string.status_saving)
            val r = TagEditor.write(context, track.uri, name, currentTags())
            if (r is TagEditor.WriteResult.NeedsPermission) {
                permLauncher.launch(IntentSenderRequest.Builder(r.intentSender).build())
            } else {
                // переиспользуем общую обработку результата
                when (r) {
                    is TagEditor.WriteResult.Ok -> {
                        runCatching {
                            LocalLibraryStore.updateTags(
                                context, track.id, title.trim(), artist.trim(), album.trim(),
                                trackNo.trim().toIntOrNull() ?: 0, year.trim().toIntOrNull() ?: 0
                            )
                        }
                        saving = false; status = context.getString(R.string.status_saved)
                    }
                    is TagEditor.WriteResult.Unsupported -> { saving = false; status = context.getString(R.string.status_format_unsupported) }
                    is TagEditor.WriteResult.Error -> { saving = false; status = r.message }
                    else -> {}
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(lc.settingsBackground)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp)) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CircleBack(lc, onBack)
                Spacer(Modifier.width(14.dp))
                Text(stringResource(R.string.tags_title), color = lc.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = VkSansDisplay)
            }
            Spacer(Modifier.height(16.dp))

            if (!supported) {
                Text(
                    stringResource(R.string.tag_format_unsupported),
                    color = lc.textSecondary, fontSize = 14.sp
                )
            } else {
                Box(Modifier.alpha(if (loading || saving) 0.5f else 1f)) {
                    Column {
                        EditField(stringResource(R.string.field_title), title, lc) { title = it }
                        EditField(stringResource(R.string.sort_artist), artist, lc) { artist = it }
                        EditField(stringResource(R.string.section_albums), album, lc) { album = it }
                        EditField(stringResource(R.string.field_album_artist), albumArtist, lc) { albumArtist = it }
                        EditField(stringResource(R.string.field_track_number), trackNo, lc, number = true) { trackNo = it.filter { c -> c.isDigit() } }
                        EditField(stringResource(R.string.field_year), year, lc, number = true) { year = it.filter { c -> c.isDigit() } }
                        EditField(stringResource(R.string.field_genre), genre, lc) { genre = it }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (saving) lc.cardSurface else lc.accent)
                        .clickable(enabled = !saving && !loading) { onSaveClick() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (saving) "…" else "Save",
                        color = if (saving) lc.textSecondary else Color.White,
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                if (status.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(status, color = lc.textSecondary, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                Text(
                    stringResource(R.string.tag_changes_hint),
                    color = lc.textTertiary, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Массовое редактирование: общие поля для нескольких выбранных треков
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun BulkTagEditScreen(tracks: List<Track>, onBack: () -> Unit) {
    val lc = LiquidTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val total = tracks.size

    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var albumArtist by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }

    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var okTotal by remember { mutableStateOf(0) }
    var failTotal by remember { mutableStateOf(0) }

    // очередь дозаписи (для API 29 — по файлу за раз) и перенос разрешения через состояние
    var remaining by remember { mutableStateOf<List<TagEditor.BulkItem>>(emptyList()) }
    var pendingFields by remember { mutableStateOf<List<Pair<org.jaudiotagger.tag.FieldKey, String>>>(emptyList()) }
    var pendingPermission by remember { mutableStateOf<android.content.IntentSender?>(null) }

    fun fields(): List<Pair<org.jaudiotagger.tag.FieldKey, String>> = buildList {
        if (artist.isNotBlank()) add(org.jaudiotagger.tag.FieldKey.ARTIST to artist.trim())
        if (album.isNotBlank()) add(org.jaudiotagger.tag.FieldKey.ALBUM to album.trim())
        if (albumArtist.isNotBlank()) add(org.jaudiotagger.tag.FieldKey.ALBUM_ARTIST to albumArtist.trim())
        if (year.isNotBlank()) add(org.jaudiotagger.tag.FieldKey.YEAR to year.trim())
        if (genre.isNotBlank()) add(org.jaudiotagger.tag.FieldKey.GENRE to genre.trim())
    }

    suspend fun drain(items: List<TagEditor.BulkItem>, f: List<Pair<org.jaudiotagger.tag.FieldKey, String>>) {
        val out = TagEditor.writePartialMany(context, items, f)
        okTotal += out.okCount; failTotal += out.failCount
        out.doneTrackIds.forEach { id ->
            runCatching {
                LocalLibraryStore.updateTagsBulk(
                    context, id, artist.trim(), album.trim(), year.trim().toIntOrNull() ?: 0
                )
            }
        }
        if (out.needPermission != null) {
            remaining = out.remaining; pendingFields = f
            status = context.getString(R.string.status_requesting_permission)
            pendingPermission = out.needPermission           // запустит лаунчер через LaunchedEffect
        } else {
            saving = false
            status = context.getString(R.string.status_done_of, okTotal, total) +
                    if (failTotal > 0) context.getString(R.string.status_skipped_suffix, failTotal) else ""
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        pendingPermission = null
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch { drain(remaining, pendingFields) }
        } else {
            saving = false; status = context.getString(R.string.status_access_denied)
        }
    }

    // Запрос разрешения вынесен в состояние, чтобы не было цикла объявлений drain↔launcher.
    LaunchedEffect(pendingPermission) {
        pendingPermission?.let { permLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }

    fun onApply() {
        if (saving) return
        val f = fields()
        if (f.isEmpty()) { status = context.getString(R.string.status_fill_one_field); return }
        saving = true; okTotal = 0; failTotal = 0; status = context.getString(R.string.status_preparing)
        pendingFields = f
        scope.launch {
            val items = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                tracks.map { TagEditor.BulkItem(it.id, it.uri, TagEditor.displayName(context, it.uri)) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                remaining = items
                val isender = TagEditor.createWriteRequest(context, items.map { it.uri })
                if (isender != null) { status = context.getString(R.string.status_requesting_permission); pendingPermission = isender; return@launch }
            }
            drain(items, f)   // API 29 — попросит разрешение по файлам по ходу
        }
    }

    Box(Modifier.fillMaxSize().background(lc.settingsBackground)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp)) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CircleBack(lc, onBack)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(stringResource(R.string.bulk_tags_title), color = lc.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = VkSansDisplay)
                    Text(stringResource(R.string.selected_tracks_count, total), color = lc.textSecondary, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.bulk_tags_hint),
                color = lc.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp)
            )
            Spacer(Modifier.height(8.dp))

            Box(Modifier.alpha(if (saving) 0.5f else 1f)) {
                Column {
                    EditField(stringResource(R.string.sort_artist), artist, lc) { artist = it }
                    EditField(stringResource(R.string.section_albums), album, lc) { album = it }
                    EditField(stringResource(R.string.field_album_artist), albumArtist, lc) { albumArtist = it }
                    EditField(stringResource(R.string.field_year), year, lc, number = true) { year = it.filter { c -> c.isDigit() } }
                    EditField(stringResource(R.string.field_genre), genre, lc) { genre = it }
                }
            }
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (saving) lc.cardSurface else lc.accent)
                    .clickable(enabled = !saving) { onApply() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (saving) "…" else "Apply to $total",
                    color = if (saving) lc.textSecondary else Color.White,
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            if (status.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(status, color = lc.textSecondary, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun EditField(label: String, value: String, lc: LiquidColors, number: Boolean = false, onChange: (String) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, color = lc.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(lc.cardSurface)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = lc.textPrimary, fontSize = 16.sp, fontFamily = VkSansText),
                cursorBrush = SolidColor(lc.accent), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = if (number)
                    androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                else androidx.compose.foundation.text.KeyboardOptions.Default,
                decorationBox = { inner ->
                    if (value.isEmpty()) Text("—", color = lc.textTertiary, fontSize = 16.sp)
                    inner()
                }
            )
        }
    }
}

@Composable
private fun CircleBack(lc: LiquidColors, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).background(if (lc.isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7), CircleShape)
            .clip(CircleShape).clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(com.lmg.vk.ui.icons.LmgGlyphs.ArrowLeftOutline28, null, tint = lc.iconDefault, modifier = Modifier.size(22.dp))
    }
}
