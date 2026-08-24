package com.lmg.vk.ui.screens

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.R
import com.lmg.vk.ui.icons.LmgDrawables
import com.lmg.vk.ui.icons.lmgVector
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.liquid.LiquidToggle
import com.lmg.vk.ui.LauncherIcon
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidTheme

// Единый акцент приложения — бледно-зелёный (заменил красный Apple-стиля).
private val Accent = Color(0xFF7FB77E)

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
internal fun requestIgnoreBatteryOptimizations(context: Context) {
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
internal fun SectionLabel(text: String) {
    val isDark = LiquidTheme.colors.isDark
    Text(
        text = text,
        color = com.lmg.vk.ui.theme.LiquidSurfaces.textPrimary(isDark),
        fontSize = com.lmg.vk.ui.theme.LiquidMetrics.SectionTitle,
        fontWeight = com.lmg.vk.ui.theme.LiquidMetrics.SectionTitleWeight,
        letterSpacing = com.lmg.vk.ui.theme.LiquidMetrics.SectionTitleSpacing,
        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
    )
}

@Composable
internal fun PlainCard(content: @Composable ColumnScope.() -> Unit) {
    val isDark = LiquidTheme.colors.isDark
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
internal fun PlainDivider() {
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
internal fun SettingsToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onSelect: (Boolean) -> Unit
) {
    val screenBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
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
internal fun SettingsActionItem(
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
internal fun LauncherIconSelector(
    selected: LauncherIcon,
    onSelect: (LauncherIcon) -> Unit,
) {
    val colors = LiquidTheme.colors
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
        Text(
            text = stringResource(R.string.app_icon),
            color = colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = stringResource(R.string.current_icon_line, stringResource(selected.titleRes)),
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
                                contentDescription = stringResource(icon.titleRes),
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
                                        imageVector = lmgVector(LmgDrawables.CheckDoubleOutline16),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            text = stringResource(icon.titleRes),
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
internal fun CrossfadeSelector(
    options: List<Int>,
    selectedMs: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = com.lmg.vk.ui.icons.LmgGlyphs.SlidersOutline28,
            contentDescription = stringResource(R.string.crossfade_section),
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
                    text = if (sec == 0) stringResource(R.string.off_label) else stringResource(R.string.seconds_short, sec),
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
internal fun SleepTimerSelector(
    options: List<Int>,
    selectedMinutes: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = com.lmg.vk.ui.icons.LmgGlyphs.ClockOutline28,
            contentDescription = stringResource(R.string.sleep_timer_section),
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
                    text = if (minutes == 0) stringResource(R.string.off_label) else stringResource(R.string.minutes_short, minutes),
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}
