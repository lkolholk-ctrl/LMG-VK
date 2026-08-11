package com.lmg.vk.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.theme.AppFontFamily
import com.lmg.vk.ui.theme.LiquidMetrics
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidSurfaces

/**
 * Статическая Hero-шапка системного раздела.
 *
 * Повторяет язык страницы артиста: крупная обложка, читаемый нижний градиент,
 * действия поверх изображения и наезжающий сверху лист контента. В отличие от
 * каталожной шапки ресурс локальный, поэтому экран не мигает при открытии и не
 * зависит от сети.
 */
@Composable
fun SectionHero(
    title: String,
    subtitle: String,
    artworkRes: Int,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    fullBleed: Boolean = false,
    onBack: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    // Все локальные Hero-обложки имеют размер 1448x1086 (ровно 4:3).
    // HeaderHeight=528dp предназначен для портретной фото/видео-шапки артиста:
    // на этих картинках он вырезал почти всю композицию по бокам. Сохраняем
    // исходный прямоугольник, но ограничиваем высоту на широких устройствах.
    val heroHeight = (screenWidth * 3f / 4f).coerceIn(264.dp, 420.dp)
    val heroModifier = if (fullBleed) {
        modifier.requiredWidth(screenWidth)
    } else {
        modifier.fillMaxWidth()
    }

    Box(modifier = heroModifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .clipToBounds(),
        ) {
            Image(
                painter = painterResource(artworkRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Black.copy(alpha = 0.15f),
                            1f to Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
            )

            onBack?.let { goBack ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(start = LiquidMetrics.ScreenPadding, top = 12.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(LiquidSurfaces.glassFill)
                        .liquidClickable(
                            pressedScale = LiquidMotion.PressIcon,
                            onClick = goBack,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = com.lmg.vk.ui.icons.LmgGlyphs.ArrowLeftOutline28,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(
                        start = LiquidMetrics.ScreenPadding,
                        end = LiquidMetrics.ScreenPadding,
                        bottom = LiquidMetrics.SheetOverlap + 8.dp,
                    ),
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = AppFontFamily,
                    fontSize = LiquidMetrics.TitleHuge,
                    lineHeight = 44.sp,
                    letterSpacing = LiquidMetrics.TitleHugeSpacing,
                    fontWeight = LiquidMetrics.TitleHugeWeight,
                    maxLines = 2,
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
                        color = Color.White.copy(alpha = 0.76f),
                        fontFamily = AppFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
                actions?.let { content ->
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        content = content,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(LiquidMetrics.SheetShape)
                .background(LiquidSurfaces.sheet(isDark))
                .padding(top = 12.dp, bottom = 4.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(LiquidSurfaces.grabber(isDark)),
            )
        }
    }
}

/** Кнопка внутри Hero: та же иерархия, что Play/Shuffle на странице артиста. */
@Composable
fun RowScope.SectionHeroAction(
    label: String,
    icon: ImageVector,
    filled: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val background = if (filled) Color.White else LiquidSurfaces.glassAction
    val content = if (filled) Color.Black else Color.White
    Row(
        modifier = Modifier
            .weight(1f)
            .height(LiquidMetrics.ActionButtonHeight)
            .alpha(if (enabled) 1f else 0.48f)
            .shadow(
                elevation = if (filled) LiquidMetrics.ButtonElevation else 2.dp,
                shape = CircleShape,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
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
            fontSize = LiquidMetrics.ActionLabel,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
