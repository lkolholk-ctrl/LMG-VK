package com.lmg.vk.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.lmg.vk.engine.CacheBrowser
import com.lmg.vk.engine.CacheEntry
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lmg.vk.R
import com.lmg.vk.engine.AppCacheManager
import com.lmg.vk.engine.CacheCategory
import com.lmg.vk.ui.glass.GlassDialog
import com.lmg.vk.ui.glass.GlassDialogButton
import com.lmg.vk.ui.icons.LmgDrawables
import com.lmg.vk.ui.icons.lmgVector
import com.lmg.vk.ui.theme.LiquidTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun CacheSettingsContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sizes by remember { mutableStateOf<Map<CacheCategory, Long>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var browsing by remember { mutableStateOf<CacheCategory?>(null) }
    var entries by remember { mutableStateOf<List<CacheEntry>?>(null) }
    var query by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<CacheEntry?>(null) }
    var browserError by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<CacheCategory?>(null) }
    val titles = mapOf(
        CacheCategory.LYRICS to stringResource(R.string.cache_lyrics),
        CacheCategory.ARTWORK to stringResource(R.string.cache_artwork),
        CacheCategory.MOTION to stringResource(R.string.cache_motion),
        CacheCategory.AUDIO to stringResource(R.string.cache_audio),
    )
    suspend fun refresh() {
        sizes = AppCacheManager.sizes(context.applicationContext)
        browsing?.let { entries = CacheBrowser.entries(context.applicationContext, it) }
    }
    LaunchedEffect(browsing) {
        entries = null
        query = ""
        browserError = null
        browsing?.let { category ->
            try { entries = CacheBrowser.entries(context.applicationContext, category) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { browserError = context.getString(R.string.cache_read_error) }
        }
    }
    LaunchedEffect(Unit) {
        try { refresh() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { message = context.getString(R.string.cache_read_error) }
    }
    fun request(category: CacheCategory?) {
        if (!busy && sizes != null) { selected = category; confirm = true }
    }
    fun size(category: CacheCategory?): String = sizes?.let {
        Formatter.formatFileSize(context, if (category == null) it.values.sum() else it[category] ?: 0L)
    } ?: context.getString(R.string.cache_calculating)

    PlainCard {
        SettingsActionItem(stringResource(R.string.cache_clear_all), size(null),
            lmgVector(LmgDrawables.DeleteOutline28), onClick = { request(null) })
    }
    Spacer(Modifier.height(20.dp))
    PlainCard {
        CacheCategory.entries.forEachIndexed { index, category ->
            if (index > 0) PlainDivider()
            SettingsActionItem(titles.getValue(category), size(category),
                lmgVector(LmgDrawables.DeleteOutline28), onClick = { if (!busy) browsing = category })
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.cache_preserved_hint), color = LiquidTheme.colors.textSecondary)
    if (busy) {
        Spacer(Modifier.height(12.dp))
        CircularProgressIndicator(Modifier.size(24.dp))
    }
    message?.let {
        Spacer(Modifier.height(12.dp))
        Text(it, color = LiquidTheme.colors.textSecondary)
    }
    browsing?.let { category ->
        GlassDialog(
            visible = true,
            onDismiss = { if (!busy) browsing = null },
            title = titles.getValue(category),
            dismissible = !busy,
            dissolveOnPrimaryClick = true,
            primaryButton = GlassDialogButton(text = stringResource(R.string.cache_done), enabled = !busy, onClick = { if (!busy) browsing = null }),
            content = {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.cache_search)) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LiquidTheme.colors.textPrimary,
                        unfocusedTextColor = LiquidTheme.colors.textPrimary,
                        unfocusedPlaceholderColor = LiquidTheme.colors.textSecondary,
                    ),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${entries?.size ?: 0} · ${size(category)}", color = LiquidTheme.colors.textSecondary)
                    TextButton(onClick = { request(category) }, enabled = !busy && sizes != null && entries != null) {
                        Text(stringResource(R.string.cache_clear))
                    }
                }
                if (busy) CircularProgressIndicator(Modifier.size(20.dp))
                browserError?.let { Text(it, color = LiquidTheme.colors.textSecondary) }
                if (entries == null && browserError == null) CircularProgressIndicator(Modifier.size(24.dp))
                entries?.let { all ->
                    val shown = remember(all, query) { all.filter { it.matches(query) } }
                    if (shown.isEmpty()) Text(stringResource(if (all.isEmpty()) R.string.cache_empty else R.string.cache_no_matches),
                        color = LiquidTheme.colors.textSecondary)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.44f).dp)) {
                        items(shown, key = { it.id }) { entry ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                entry.preview?.let { file ->
                                    AsyncImage(model = file, contentDescription = null, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.width(10.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis, color = LiquidTheme.colors.textPrimary)
                                    if (entry.artist.isNotBlank()) Text(entry.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = LiquidTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                                    Text("${entry.source} · ${Formatter.formatFileSize(context, entry.bytes)}",
                                        color = LiquidTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { deleting = entry }, enabled = !busy) {
                                    Icon(lmgVector(LmgDrawables.DeleteOutline28), contentDescription = context.getString(R.string.cache_delete_named, entry.title),
                                        tint = LiquidTheme.colors.textSecondary)
                                }
                            }
                        }
                    }
                    if (category == CacheCategory.ARTWORK && (sizes?.get(category) ?: 0) > all.sumOf { it.bytes } + 65536L) {
                        Text(stringResource(R.string.cache_legacy_artwork), style = MaterialTheme.typography.bodySmall,
                            color = LiquidTheme.colors.textSecondary)
                    }
                }
            },
        )
    }
    deleting?.let { entry ->
        GlassDialog(visible = true, onDismiss = { deleting = null },
            title = stringResource(R.string.cache_delete_named, entry.title),
            message = stringResource(R.string.cache_delete_hint),
            primaryButton = GlassDialogButton(text = stringResource(R.string.cache_delete), onClick = {
                val category = browsing ?: return@GlassDialogButton
                deleting = null
                busy = true
                browserError = null
                scope.launch {
                    try {
                        CacheBrowser.remove(context.applicationContext, category, entry)
                        refresh()
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) {
                        browserError = context.getString(R.string.cache_clear_error)
                        try { refresh() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                    }
                    finally { busy = false }
                }
            }),
            secondaryButton = GlassDialogButton(text = stringResource(R.string.action_cancel), onClick = { deleting = null }),
        )
    }
    GlassDialog(
        visible = confirm,
        onDismiss = { confirm = false },
        title = stringResource(R.string.cache_confirm, selected?.let(titles::getValue)
            ?: stringResource(R.string.cache_all)),
        message = stringResource(R.string.cache_preserved_hint),
        primaryButton = GlassDialogButton(text = stringResource(R.string.cache_clear), onClick = {
            val category = selected
            confirm = false
            busy = true
            message = null
            scope.launch {
                try {
                    AppCacheManager.clear(context.applicationContext, category)
                    refresh()
                    message = context.getString(R.string.cache_cleared)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    message = context.getString(R.string.cache_clear_error)
                    browserError = message
                    try { refresh() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                } finally { busy = false }
            }
        }),
        secondaryButton = GlassDialogButton(text = stringResource(R.string.action_cancel),
            onClick = { confirm = false }),
    )
}
