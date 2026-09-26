package com.lmg.vk.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
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
    var selected by remember { mutableStateOf<CacheCategory?>(null) }
    val titles = mapOf(
        CacheCategory.LYRICS to stringResource(R.string.cache_lyrics),
        CacheCategory.ARTWORK to stringResource(R.string.cache_artwork),
        CacheCategory.MOTION to stringResource(R.string.cache_motion),
        CacheCategory.AUDIO to stringResource(R.string.cache_audio),
    )
    suspend fun refresh() { sizes = AppCacheManager.sizes(context.applicationContext) }
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
                lmgVector(LmgDrawables.DeleteOutline28), onClick = { request(category) })
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
                    try { refresh() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                } finally { busy = false }
            }
        }),
        secondaryButton = GlassDialogButton(text = stringResource(R.string.action_cancel),
            onClick = { confirm = false }),
    )
}
