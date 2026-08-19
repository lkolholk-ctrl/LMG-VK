package com.lmg.vk.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.theme.VkSansDisplay
import com.lmg.vk.ui.theme.VkSansText

/**
 * Reusable custom dialog that matches the LMG Liquid design language.
 * Uses Compose Dialog window with semi-transparent backdrop and glass surfaces.
 */
@Composable
fun GlassDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    icon: ImageVector? = null,
    iconTint: Color = Color(0xFFFC3C44),
    title: String,
    message: String? = null,
    primaryButton: GlassDialogButton? = null,
    secondaryButton: GlassDialogButton? = null,
    dismissible: Boolean = true,
    content: (@Composable () -> Unit)? = null,
) {
    if (!visible) return

    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val colors = LiquidTheme.colors
        val isDark = colors.isDark
        val dialogBg = if (isDark) Color(0xFF1C1C1E) else Color.White
        val dialogBorder = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = dismissible,
                ) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(dialogBg)
                    .border(1.dp, dialogBorder, RoundedCornerShape(28.dp))
                    .padding(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { }, // prevent dismiss when tapping inside
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Icon Header
                    if (icon != null) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(iconTint.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Title
                    Text(
                        text = title,
                        color = colors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = VkSansDisplay,
                        textAlign = TextAlign.Center,
                    )

                    // Message
                    if (!message.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = message,
                            color = colors.textSecondary,
                            fontSize = 14.sp,
                            fontFamily = VkSansText,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                        )
                    }

                    // Optional Custom Body Content
                    if (content != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        content()
                    }

                    // Buttons
                    if (primaryButton != null || secondaryButton != null) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Secondary (left)
                            if (secondaryButton != null) {
                                val secondaryBtnBg = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF2F2F7)
                                val secondaryBtnBorder = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(23.dp))
                                        .background(secondaryBtnBg)
                                        .border(1.dp, secondaryBtnBorder, RoundedCornerShape(23.dp))
                                        .liquidClickable(
                                            pressedScale = LiquidMotion.PressButton,
                                            enabled = secondaryButton.enabled,
                                        ) { secondaryButton.onClick() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = secondaryButton.text,
                                        color = if (secondaryButton.enabled) colors.textPrimary else colors.textTertiary,
                                        fontFamily = VkSansText,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                    )
                                }
                            }

                            // Primary (right)
                            if (primaryButton != null) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(23.dp))
                                        .background(
                                            if (primaryButton.enabled) {
                                                primaryButton.backgroundColor
                                            } else {
                                                primaryButton.backgroundColor.copy(alpha = 0.4f)
                                            }
                                        )
                                        .liquidClickable(
                                            pressedScale = LiquidMotion.PressButton,
                                            enabled = primaryButton.enabled,
                                        ) { primaryButton.onClick() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = primaryButton.text,
                                        color = if (primaryButton.enabled) primaryButton.textColor else primaryButton.textColor.copy(alpha = 0.6f),
                                        fontFamily = VkSansText,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom container dialog for complex layouts (e.g. Account switcher, Edit profile, Artist chooser)
 * with top header, scrollable/expandable content and action buttons.
 */
@Composable
fun GlassCustomDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    icon: ImageVector? = null,
    iconTint: Color = LiquidTheme.colors.accent,
    title: String,
    subtitle: String? = null,
    dismissible: Boolean = true,
    primaryButton: GlassDialogButton? = null,
    secondaryButton: GlassDialogButton? = null,
    content: @Composable () -> Unit,
) {
    if (!visible) return

    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val colors = LiquidTheme.colors
        val isDark = colors.isDark
        val dialogBg = if (isDark) Color(0xFF1C1C1E) else Color.White
        val dialogBorder = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = dismissible,
                ) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(dialogBg)
                    .border(1.dp, dialogBorder, RoundedCornerShape(28.dp))
                    .padding(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Top Icon
                    if (icon != null) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(iconTint.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Title
                    Text(
                        text = title,
                        color = colors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = VkSansDisplay,
                        textAlign = TextAlign.Center,
                    )

                    // Subtitle
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = subtitle,
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            fontFamily = VkSansText,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Body Content
                    content()

                    // Optional Bottom Buttons
                    if (primaryButton != null || secondaryButton != null) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (secondaryButton != null) {
                                val secondaryBtnBg = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF2F2F7)
                                val secondaryBtnBorder = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(23.dp))
                                        .background(secondaryBtnBg)
                                        .border(1.dp, secondaryBtnBorder, RoundedCornerShape(23.dp))
                                        .liquidClickable(
                                            pressedScale = LiquidMotion.PressButton,
                                            enabled = secondaryButton.enabled,
                                        ) { secondaryButton.onClick() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = secondaryButton.text,
                                        color = if (secondaryButton.enabled) colors.textPrimary else colors.textTertiary,
                                        fontFamily = VkSansText,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                    )
                                }
                            }

                            if (primaryButton != null) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(23.dp))
                                        .background(
                                            if (primaryButton.enabled) {
                                                primaryButton.backgroundColor
                                            } else {
                                                primaryButton.backgroundColor.copy(alpha = 0.4f)
                                            }
                                        )
                                        .liquidClickable(
                                            pressedScale = LiquidMotion.PressButton,
                                            enabled = primaryButton.enabled,
                                        ) { primaryButton.onClick() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = primaryButton.text,
                                        color = if (primaryButton.enabled) primaryButton.textColor else primaryButton.textColor.copy(alpha = 0.6f),
                                        fontFamily = VkSansText,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class GlassDialogButton(
    val text: String,
    val onClick: () -> Unit,
    val backgroundColor: Color = Color(0xFFFC3C44),
    val textColor: Color = Color.White,
    val enabled: Boolean = true,
)
