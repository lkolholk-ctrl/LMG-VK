package com.lmg.vk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lmg.vk.engine.backend.VkAccountSummary
import com.lmg.vk.ui.glass.GlassCustomDialog
import com.lmg.vk.ui.glass.GlassDialogButton
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.icons.LmgDrawables
import com.lmg.vk.ui.icons.lmgVector
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.theme.VkSansText

private val DestructiveColor = Color(0xFFFC3C44)

/**
 * Custom Liquid/Glass modal dialog for switching VK accounts and managing sessions.
 */
@Composable
fun VkAccountsDialog(
    visible: Boolean,
    accounts: List<VkAccountSummary>,
    errorMessage: String? = null,
    onSelectAccount: (VkAccountSummary) -> Unit,
    onRemoveAccount: (VkAccountSummary) -> Unit,
    onAddAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val colors = LiquidTheme.colors
    val isDark = colors.isDark

    GlassCustomDialog(
        visible = visible,
        onDismiss = onDismiss,
        icon = lmgVector(LmgDrawables.Users3Outline28),
        iconTint = colors.accent,
        title = "VK accounts",
        subtitle = when {
            accounts.size <= 1 -> "Manage saved accounts or add another session"
            else -> "${accounts.size} saved accounts on this device"
        },
        primaryButton = GlassDialogButton(
            text = "Close",
            backgroundColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF2F2F7),
            textColor = colors.textPrimary,
            onClick = onDismiss,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Accounts List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(accounts, key = { it.userId }) { account ->
                    AccountPickerItem(
                        account = account,
                        onSelect = { onSelectAccount(account) },
                        onRemove = { onRemoveAccount(account) },
                    )
                }
            }

            // Error notice
            if (!errorMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DestructiveColor.copy(alpha = 0.12f))
                        .border(1.dp, DestructiveColor.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = errorMessage,
                        color = DestructiveColor,
                        fontFamily = VkSansText,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }

            // Add Account Glass Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.accent.copy(alpha = 0.12f))
                    .border(1.dp, colors.accent.copy(alpha = 0.26f), RoundedCornerShape(18.dp))
                    .liquidClickable(
                        pressedScale = LiquidMotion.PressButton,
                        onClick = onAddAccount,
                    )
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = lmgVector(LmgDrawables.UserAddOutline28),
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Add VK account",
                    color = colors.accent,
                    fontFamily = VkSansText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun AccountPickerItem(
    account: VkAccountSummary,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LiquidTheme.colors
    val isDark = colors.isDark

    val cardBg = if (account.isActive) {
        colors.accent.copy(alpha = 0.14f)
    } else {
        LiquidSurfaces.card(isDark)
    }

    val cardBorder = if (account.isActive) {
        colors.accent.copy(alpha = 0.40f)
    } else {
        if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
            .liquidClickable(
                pressedScale = LiquidMotion.PressButton,
                enabled = !account.isActive,
                onClick = onSelect,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar with optional active indicator ring
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.textTertiary.copy(alpha = 0.14f))
                .then(
                    if (account.isActive) {
                        Modifier.border(2.dp, colors.accent, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (account.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(account.avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = account.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = lmgVector(LmgDrawables.UserOutline28),
                    contentDescription = null,
                    tint = if (account.isActive) colors.accent else colors.iconMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = account.displayName,
                    fontFamily = VkSansText,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (account.isActive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.accent.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Active",
                            fontFamily = VkSansText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent,
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            val subtitleText = when {
                account.isExpired -> "Sign-in expired"
                account.username.isNotBlank() -> "vk.com/${account.username}"
                else -> "VK ID ${account.userId}"
            }
            Text(
                text = subtitleText,
                fontFamily = VkSansText,
                color = if (account.isExpired) DestructiveColor else colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        // Remove Account Button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
                )
                .liquidClickable(
                    pressedScale = LiquidMotion.PressButton,
                    onClick = onRemove,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = lmgVector(LmgDrawables.DeleteOutline28),
                contentDescription = "Remove account",
                tint = DestructiveColor,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
