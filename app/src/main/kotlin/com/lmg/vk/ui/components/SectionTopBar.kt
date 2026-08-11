package com.lmg.vk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.rememberWindowInfo
import com.lmg.vk.ui.theme.AppFontFamily
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.theme.LiquidTheme

/**
 * Компактная шапка системного раздела LMG VK.
 *
 * В отличие от каталожных экранов артиста и альбома здесь нет сетевой обложки,
 * поэтому большая фото-шапка только отнимала место и смешивала две разные роли.
 * Эта панель оставляет привычную типографику, круглую кнопку возврата и действия,
 * но сразу отдаёт основную часть экрана содержимому.
 */
@Composable
fun SectionTopBar(
    title: String,
    subtitle: String,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val compact = rememberWindowInfo().useSideBySide
    val colors = LiquidTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = if (compact) 10.dp else 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onBack?.let { goBack ->
                Box(
                    modifier = Modifier
                        .size(if (compact) 36.dp else 40.dp)
                        .clip(CircleShape)
                        .background(LiquidSurfaces.card(isDark))
                        .liquidClickable(
                            pressedScale = LiquidMotion.PressIcon,
                            onClick = goBack,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = com.lmg.vk.ui.icons.LmgGlyphs.ArrowLeftOutline28,
                        contentDescription = "Back",
                        tint = colors.iconDefault,
                        modifier = Modifier.size(if (compact) 18.dp else 20.dp),
                    )
                }
                Spacer(Modifier.width(if (compact) 12.dp else 14.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontFamily = AppFontFamily,
                    fontSize = if (compact) 22.sp else 28.sp,
                    lineHeight = if (compact) 26.sp else 32.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.then(
                        if (onTitleClick != null) {
                            Modifier.liquidClickable(
                                pressedScale = LiquidMotion.PressButton,
                                onClick = onTitleClick,
                            )
                        } else {
                            Modifier
                        },
                    ),
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = colors.textSecondary,
                        fontFamily = AppFontFamily,
                        fontSize = if (compact) 12.sp else 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }

        actions?.let { content ->
            Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

/** Действие под компактной шапкой: акцентное либо спокойное карточное. */
@Composable
fun RowScope.SectionTopBarAction(
    label: String,
    icon: ImageVector,
    filled: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LiquidTheme.colors
    val background = if (filled) colors.accent else colors.cardSurface
    val content = if (filled) Color.White else colors.textPrimary

    Row(
        modifier = Modifier
            .weight(1f)
            .height(42.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(CircleShape)
            .background(background)
            .liquidClickable(
                enabled = enabled,
                pressedScale = LiquidMotion.PressButton,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = content,
            fontFamily = AppFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
