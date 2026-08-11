package com.lmg.vk.ui.screens

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.lmg.vk.engine.AppSettings
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.PlayerSettings
import com.lmg.vk.R
import com.lmg.vk.ui.components.SectionHero
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.liquid.LiquidToggle
import com.lmg.vk.ui.LauncherIcon
import com.lmg.vk.ui.LauncherIconManager
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidTheme
import kotlinx.coroutines.launch

// Единый акцент приложения — бледно-зелёный (заменил красный Apple-стиля).
private val Accent = Color(0xFF7FB77E)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    /** Открыть онбординг рекомендаций ВКонтакте (`audio.recommendationsOnboarding`). */
    onOpenRecommendationsOnboarding: () -> Unit = {},
    /** Открыть экран отладочного лога (диагностика воспроизведения без adb). */
    onOpenDebugLog: () -> Unit = {},
    showBack: Boolean = true,
    backdrop: LayerBackdrop
) {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    val sleepTimerMinutes by AppSettings.sleepTimerMinutes.collectAsState()
    val sleepOptions = listOf(0, 15, 30, 45, 60, 90)

    val themeMode by PlayerController.themeMode.collectAsState()
    val themeLabels = listOf("System", "Dark", "Light")

    val lc = LiquidTheme.colors

    // Широкое окно (телефон-альбом ИЛИ планшет). В портрете layout не меняется.
    val win = com.lmg.vk.ui.rememberWindowInfo()
    // В альбоме/на планшете всё компактнее: секции ставим ближе (меньше вертикальный
    // ход взгляда). Только для широкого окна — портрет остаётся как был.
    val sectionGap = if (win.useSideBySide) 20.dp else 28.dp

    Box(modifier = Modifier.fillMaxSize().background(lc.settingsBackground)) {
        Column(
            modifier = Modifier
                // В альбоме/на планшете вертикальный список настроек не тянем на
                // всю ширину (растянутые карточки некрасивы, взгляд возит далеко) —
                // ограничиваем 640dp и центрируем. Портрет остаётся как был.
                .then(
                    if (win.useSideBySide)
                        Modifier.align(Alignment.TopCenter).fillMaxHeight().widthIn(max = 640.dp)
                    else Modifier.fillMaxSize()
                )
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
        ) {
            // 5 быстрых тапов по заголовку по-прежнему открывают скрытый debug UI.
            var dbgTaps by remember { mutableStateOf(0) }
            var dbgLastTapAt by remember { mutableStateOf(0L) }
            SectionHero(
                title = "Settings",
                subtitle = "Account, playback and appearance",
                artworkRes = R.drawable.hero_settings,
                isDark = lc.isDark,
                fullBleed = true,
                onBack = if (showBack) onBack else null,
                onTitleClick = {
                    val now = System.currentTimeMillis()
                    dbgTaps = if (now - dbgLastTapAt < 1200L) dbgTaps + 1 else 1
                    dbgLastTapAt = now
                    if (dbgTaps >= 5) {
                        dbgTaps = 0
                        val enabled = !AppSettings.debugUiEnabled.value
                        AppSettings.setDebugUiEnabled(enabled)
                        android.widget.Toast.makeText(
                            context,
                            if (enabled) "Debug tools: ON" else "Debug tools: OFF",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Шапка-профиль: аватарка + имя, тап → профиль (как у Apple/TG) ──
            run {
                val avatarUrl by com.lmg.vk.engine.backend.MusicAuth
                    .avatarUrl.collectAsState()
                val profileName by com.lmg.vk.engine.backend.MusicAuth
                    .profileName.collectAsState()
                val userEmail by com.lmg.vk.engine.backend.MusicAuth
                    .userEmail.collectAsState()
                val displayName = when {
                    !profileName.isNullOrBlank() -> profileName!!
                    userEmail != null -> userEmail!!.substringBefore("@")
                        .replaceFirstChar { it.uppercase() }
                    else -> "Guest"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (lc.isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7))
                        .liquidClickable { onOpenProfile() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (lc.isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!avatarUrl.isNullOrBlank()) {
                            coil.compose.AsyncImage(
                                model = avatarUrl,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = com.lmg.vk.ui.icons.LmgGlyphs.UserOutline28,
                                contentDescription = null,
                                tint = lc.iconMuted,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = displayName,
                            color = lc.textPrimary,
                            fontSize = if (win.useSideBySide) 15.sp else 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Account, premium & data",
                            color = lc.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(sectionGap))

            // APP ICON — launcher-алиасы переключаются без перезапуска activity.
            SectionLabel("APP ICON")
            var launcherIcon by remember { mutableStateOf(LauncherIconManager.current(context)) }
            PlainCard {
                LauncherIconSelector(
                    selected = launcherIcon,
                    onSelect = { icon ->
                        if (LauncherIconManager.select(context, icon)) {
                            launcherIcon = icon
                            android.widget.Toast.makeText(
                                context,
                                "Иконка «${icon.title}» выбрана",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(sectionGap))

            // PLAYBACK
            SectionLabel("PLAYBACK")

            // Из PLAYBACK убраны: Hide Explicit, Audio Output, Warm Sound,
            // Haptic Music (+ селектор силы) и вход на экран Audio
            // (EQ/Bass/Loudness/Compressor/Limiter). Карточка опустела целиком,
            // поэтому удалена вместе с ней.
            // ПОЧЕМУ: всё это упиралось в нативную библиотеку automix_juce,
            // которой в сборке НЕТ (линкуется только liblmg.so, без JUCE/Oboe) —
            // пункты стояли в UI, но не работали. Не возвращать, пока JUCE-цепочка
            // реально не собирается. Реализации в engine/ намеренно оставлены.

            PlainCard {
                SettingsActionItem(
                    title = "Ignore Battery Optimization",
                    subtitle = "Prevents background stutter (Doze). Recommended for music",
                    icon = com.lmg.vk.ui.icons.LmgGlyphs.WarningTriangleOutline28,
                    onClick = { requestIgnoreBatteryOptimizations(context) }
                )
            }

            Spacer(modifier = Modifier.height(sectionGap))

            // ── ACCESSIBILITY ──

            PlainCard {
                SleepTimerSelector(
                    options = sleepOptions,
                    selectedMinutes = sleepTimerMinutes,
                    onSelect = { AppSettings.setSleepTimer(it) }
                )
            }

            Spacer(modifier = Modifier.height(sectionGap))

            // AUDIO — остался только кроссфейд (он работает через форк media3,
            // без JUCE). Прежние подписи про preload/listen-together здесь
            // устарели: те блоки удалены, см. комментарий ниже.
            SectionLabel("AUDIO")

            val crossfadeMs by PlayerSettings.crossfadeMs.collectAsState()
            PlainCard {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    CrossfadeSelector(
                        options = listOf(0, 4, 9, 12, 15, 18),
                        selectedMs = crossfadeMs,
                        onSelect = { PlayerSettings.setCrossfadeMs(it * 1000) }
                    )
                    Text(
                        text = "Fade between tracks. Off plays them back to back.",
                        color = lc.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(sectionGap))

            // Здесь была карточка AutoMix / Sound Check / Keep favourites offline —
            // удалена целиком (все три пункта висели на отсутствующем automix_juce).
            // AutoMix убран ВРЕМЕННО: пользователь вернёт его после интеграции
            // ML-модели (1M параметров) в форк media3 — реализацию в
            // engine/automix/ и PlayerSettings.autoMix НЕ удалять.
            //
            // Ниже была кнопка «Reset Wave Preferences» — тоже удалена по просьбе
            // пользователя (MusicBackend.resetWave() при этом сохранён).

            // Разделы QUALITY & CACHE и LISTEN TOGETHER удалены целиком по
            // просьбе пользователя (вместе с их содержимым: выбор битрейта,
            // размер/очистка аудиокэша, preload следующего трека, комнаты
            // совместного прослушивания, continuity и общие плейлисты).
            // Не возвращать без отдельной просьбы.

            // ПРОФИЛЬ ВКОНТАКТЕ
            SectionLabel("VK PROFILE")

            val broadcastToStatus by AppSettings.broadcastToStatus.collectAsState()
            val vkLoggedIn by com.lmg.vk.engine.backend.MusicAuth.isLoggedIn.collectAsState()

            PlainCard {
                SettingsToggleItem(
                    title = "Транслировать в статус",
                    icon = com.lmg.vk.ui.icons.LmgGlyphs.MusicNoteWaveOutline28,
                    // Честно про последствия: это единственная настройка в списке,
                    // которая меняет что-то ВНЕ приложения и видна другим людям.
                    // Про «не вошли» пишем прямо, потому что тумблер сам по себе
                    // ничего не сделает — статус ставить некуда.
                    subtitle = if (!vkLoggedIn) {
                        "Войдите в аккаунт ВКонтакте, чтобы включить трансляцию"
                    } else {
                        "Играющий трек будет виден в вашем профиле ВКонтакте — " +
                            "его увидят друзья. Статус снимается, когда музыка " +
                            "останавливается"
                    },
                    selected = broadcastToStatus,
                    onSelect = { AppSettings.setBroadcastToStatus(it) }
                )
            }

            Spacer(modifier = Modifier.height(sectionGap))

            // NETWORK
            SectionLabel("NETWORK")

            val proxyEnabled by com.lmg.vk.network.proxy.VkProxyRepository.enabled
                .collectAsState()
            val proxyState by com.lmg.vk.network.proxy.VkProxyRepository.state
                .collectAsState()
            val proxyScope = rememberCoroutineScope()

            PlainCard {
                SettingsToggleItem(
                    title = "Обход блокировок",
                    icon = com.lmg.vk.ui.icons.LmgGlyphs.LockOutline28,
                    subtitle = when (val state = proxyState) {
                        is com.lmg.vk.network.proxy.VkProxyState.Available ->
                            "Готово: ${state.ips.size} адр., " +
                                "${state.allowedDomains.size} доменов" +
                                if (state.certificates.isEmpty()) "" else ", пиннинг"
                        is com.lmg.vk.network.proxy.VkProxyState.Loading -> "Загружаю настройки…"
                        is com.lmg.vk.network.proxy.VkProxyState.FailedToLoad ->
                            "Настройки не загрузились — нажмите «Обновить»"
                    },
                    selected = proxyEnabled,
                    onSelect = { com.lmg.vk.network.proxy.VkProxyRepository.setEnabled(it) }
                )
                PlainDivider()
                SettingsActionItem(
                    title = "Обновить адреса",
                    subtitle = "Перечитать список серверов и сертификатов",
                    icon = com.lmg.vk.ui.icons.LmgGlyphs.RefreshOutline28,
                    onClick = {
                        proxyScope.launch {
                            com.lmg.vk.network.proxy.VkProxyRepository.refresh()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(sectionGap))

            // РЕКОМЕНДАЦИИ ВКОНТАКТЕ (порт онбординга VK X, docs/vkx-port/01-music.md §11).
            // Отдельный раздел, а не пункт внутри VK PROFILE: этот экран меняет
            // не аккаунт, а музыкальную выдачу, и требует входа в аккаунт.
            SectionLabel("RECOMMENDATIONS")

            val onboardingLoggedIn by com.lmg.vk.engine.backend.MusicAuth.isLoggedIn
                .collectAsState()

            PlainCard {
                SettingsActionItem(
                    title = "Настроить рекомендации",
                    // Про «не вошли» говорим прямо: без токена метод вернёт
                    // ошибку, и открывать пустой экран смысла нет.
                    subtitle = if (!onboardingLoggedIn) {
                        "Войдите в аккаунт ВКонтакте, чтобы выбрать исполнителей"
                    } else {
                        "Отметить исполнителей, под которых ВКонтакте подстроит выдачу"
                    },
                    icon = com.lmg.vk.ui.icons.LmgGlyphs.SlidersOutline28,
                    onClick = {
                        if (onboardingLoggedIn) onOpenRecommendationsOnboarding()
                    }
                )
            }

            Spacer(modifier = Modifier.height(sectionGap))

            // ДИАГНОСТИКА. Раздел видим ВСЕГДА, а не под debug-тапами: лог нужен
            // именно тогда, когда воспроизведение не работает, и объяснять по
            // переписке «тапните 5 раз по заголовку» — лишний шаг к ответу.
            SectionLabel("DIAGNOSTICS")

            PlainCard {
                SettingsActionItem(
                    title = "Отладочный лог",
                    subtitle = "Что происходит при запуске трека. Внутри — " +
                        "«Очистить», затем воспроизведите трек и пришлите лог",
                    icon = com.lmg.vk.ui.icons.LmgGlyphs.DocumentTextOutline28,
                    onClick = onOpenDebugLog
                )
            }

            Spacer(modifier = Modifier.height(sectionGap))

            // CROSSFADE
            SectionLabel("APPEARANCE & ACCESSIBILITY")

            PlainCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    themeLabels.forEachIndexed { index, label ->
                        val isSelected = themeMode == index
                        val isDark = lc.isDark
                        val itemBg = if (isSelected) Accent else (if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7))
                        val unselectedTextColor = if (isDark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.45f)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .background(
                                    itemBg,
                                    RoundedCornerShape(50)
                                )
                                .clip(RoundedCornerShape(50))
                                .liquidClickable(
                                    pressedScale = LiquidMotion.PressButton,
                                    onClick = { PlayerController.setThemeMode(index) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val textColor by animateColorAsState(
                                targetValue = if (isSelected) Color.White
                                else unselectedTextColor,
                                animationSpec = tween(200),
                                label = "themeText"
                            )
                            Text(
                                text = label,
                                color = textColor,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(sectionGap))

            // ── AUTOMIX & SOUND ──

            val increaseContrast by PlayerSettings.increaseContrast.collectAsState()
            PlainCard {
                SettingsToggleItem(
                    title = "Increase Contrast",
                    subtitle = "Stronger text & less glass transparency",
                    icon = com.lmg.vk.ui.icons.LmgGlyphs.GearOutline24,
                    selected = increaseContrast,
                    onSelect = { PlayerSettings.setIncreaseContrast(it) }
                )
            }

            Spacer(modifier = Modifier.height(sectionGap))

            // Нижний отступ под плавающий таб-бар (Settings теперь вкладка).
            Spacer(modifier = Modifier.height(110.dp))
        }
    }
}

/**
 * Запрос исключения из оптимизации батареи — МАКСИМАЛЬНО защищённо. На части
 * прошивок/версий Android этот интент вырезан или кидает SecurityException
 * (известны стабильные краши у приложений без страховок), поэтому:
 *  - каждый шаг в runCatching (никогда не роняем приложение);
 *  - FLAG_ACTIVITY_NEW_TASK (не зависим от типа контекста);
 *  - цепочка фолбэков: системный диалог → общий список исключений →
 *    страница приложения в настройках. Ничего не персистим — повторный
 *    запуск приложения ни при каком исходе не затрагивается.
 */
private fun requestIgnoreBatteryOptimizations(context: Context) {
    val pkg = context.packageName
    val alreadyIgnoring = runCatching {
        context.getSystemService(android.os.PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(pkg) == true
    }.getOrDefault(false)

    fun tryStart(intent: android.content.Intent): Boolean = runCatching {
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    if (!alreadyIgnoring) {
        // 1) Прямой системный диалог (нужен REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        //    в манифесте — добавлен; без него часть прошивок кидает SecurityException).
        if (tryStart(
                android.content.Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:$pkg")
                )
            )
        ) return
    }
    // 2) Общий список исключений (или уже в исключениях — показать, где это).
    if (tryStart(
            android.content.Intent(
                android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            )
        )
    ) return
    // 3) Последний фолбэк — страница приложения в настройках.
    tryStart(
        android.content.Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:$pkg")
        )
    )
}

// ── UI Components ──

@Composable
private fun SectionLabel(text: String) {
    val isDark = LiquidTheme.colors.isDark
    // Тот же заголовок, что на экранах артиста и альбома: раньше это была мелкая
    // подпись капсом, из-за чего разделы читались как служебные пометки, а не как
    // структура экрана.
    Text(
        text = text.lowercase().replaceFirstChar { it.uppercase() },
        color = com.lmg.vk.ui.theme.LiquidSurfaces.textPrimary(isDark),
        fontSize = com.lmg.vk.ui.theme.LiquidMetrics.SectionTitle,
        fontWeight = com.lmg.vk.ui.theme.LiquidMetrics.SectionTitleWeight,
        letterSpacing = com.lmg.vk.ui.theme.LiquidMetrics.SectionTitleSpacing,
        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
    )
}

@Composable
private fun PlainCard(content: @Composable ColumnScope.() -> Unit) {
    val isDark = LiquidTheme.colors.isDark
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Тень до заливки: после clip/background она обрезалась бы формой.
            .shadow(
                elevation = com.lmg.vk.ui.theme.LiquidMetrics.CardElevation,
                shape = com.lmg.vk.ui.theme.LiquidMetrics.CardShape,
                ambientColor = com.lmg.vk.ui.theme.LiquidSurfaces.shadowTint(isDark),
                spotColor = com.lmg.vk.ui.theme.LiquidSurfaces.shadowTint(isDark)
            )
            .background(
                com.lmg.vk.ui.theme.LiquidSurfaces.card(isDark),
                com.lmg.vk.ui.theme.LiquidMetrics.CardShape
            )
            .padding(vertical = 4.dp),
        content = content
    )
}

@Composable
private fun PlainDivider() {
    val isDark = LiquidTheme.colors.isDark
    val dividerColor = com.lmg.vk.ui.theme.LiquidSurfaces.divider(isDark)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(dividerColor)
    )
}

@Composable
private fun SettingsToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onSelect: (Boolean) -> Unit
) {
    val screenBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    // Компактный заголовок строки в широком окне (телефон-альбом/планшет).
    val compact = com.lmg.vk.ui.rememberWindowInfo().useSideBySide
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidClickable { onSelect(!selected) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LiquidTheme.colors.iconDefault,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = LiquidTheme.colors.textPrimary,
                fontSize = if (compact) 14.sp else 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = LiquidTheme.colors.textSecondary,
                fontSize = 12.sp
            )
        }
        LiquidToggle(selected = { selected }, onSelect = onSelect, backdrop = screenBackdrop)
    }
}

@Composable
private fun SettingsActionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    // Компактный заголовок строки в широком окне (телефон-альбом/планшет).
    val compact = com.lmg.vk.ui.rememberWindowInfo().useSideBySide
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidClickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LiquidTheme.colors.iconDefault,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = LiquidTheme.colors.textPrimary,
                fontSize = if (compact) 14.sp else 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = LiquidTheme.colors.textSecondary,
                fontSize = 12.sp
            )
        }
        Icon(
            imageVector = com.lmg.vk.ui.icons.LmgGlyphs.ChevronRightOutline24,
            contentDescription = null,
            tint = LiquidTheme.colors.iconDefault,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Сетка доступных ярлыков: названия — оттенки из предоставленного набора. */
@Composable
private fun LauncherIconSelector(
    selected: LauncherIcon,
    onSelect: (LauncherIcon) -> Unit,
) {
    val colors = LiquidTheme.colors
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
        Text(
            text = "Иконка приложения",
            color = colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = "Сейчас: ${selected.title}",
            color = colors.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Spacer(Modifier.height(10.dp))
        LauncherIcon.values().toList().chunked(4).forEach { rowIcons ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowIcons.forEach { icon ->
                    val isSelected = icon == selected
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .liquidClickable(pressedScale = LiquidMotion.PressIcon) {
                                onSelect(icon)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Accent else colors.glassBorder,
                                    shape = RoundedCornerShape(12.dp),
                                ),
                        ) {
                            Image(
                                painter = painterResource(icon.drawableRes),
                                contentDescription = icon.title,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(5.dp)
                                        .size(20.dp)
                                        .background(Accent, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            text = icon.title,
                            color = if (isSelected) colors.textPrimary else colors.textSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                        )
                    }
                }
                repeat(4 - rowIcons.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CrossfadeSelector(
    options: List<Int>,
    selectedMs: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = com.lmg.vk.ui.icons.LmgGlyphs.SlidersOutline28,
            contentDescription = "Crossfade",
            tint = LiquidTheme.colors.iconDefault,
            modifier = Modifier.size(22.dp),
        )
        options.forEach { sec ->
            val isSelected = selectedMs / 1000 == sec
            val isDark = LiquidTheme.colors.isDark
            val itemBg = if (isSelected) Accent else (if (isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA))
            val unselectedTextColor =
                if (isDark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.45f)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .background(itemBg, RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .liquidClickable(
                        pressedScale = LiquidMotion.PressButton,
                        onClick = { onSelect(sec) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else unselectedTextColor,
                    animationSpec = tween(200),
                    label = "crossfadeText"
                )
                Text(
                    text = if (sec == 0) "Off" else "${sec}s",
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun SleepTimerSelector(
    options: List<Int>,
    selectedMinutes: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = com.lmg.vk.ui.icons.LmgGlyphs.ClockOutline28,
            contentDescription = "Sleep timer",
            tint = LiquidTheme.colors.iconDefault,
            modifier = Modifier.size(22.dp),
        )
        options.forEach { minutes ->
            val isSelected = selectedMinutes == minutes
            val isDark = LiquidTheme.colors.isDark
            val itemBg = if (isSelected) Accent else (if (isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA))
            val unselectedTextColor = if (isDark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.45f)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .background(
                        itemBg,
                        RoundedCornerShape(50)
                    )
                    .clip(RoundedCornerShape(50))
                    .liquidClickable(
                        pressedScale = LiquidMotion.PressButton,
                        onClick = { onSelect(minutes) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White
                    else unselectedTextColor,
                    animationSpec = tween(200),
                    label = "sleepText"
                )
                Text(
                    text = if (minutes == 0) "Off" else "${minutes}m",
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}
