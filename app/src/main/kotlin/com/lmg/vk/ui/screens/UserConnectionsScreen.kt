package com.lmg.vk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lmg.vk.ui.components.DetailTopBar
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.icons.LmgGlyphs
import com.lmg.vk.ui.theme.LiquidMetrics
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.theme.VkSansText
import com.lmg.vk.ui.viewmodel.UserConnectionEntry
import com.lmg.vk.ui.viewmodel.UserConnectionsKind
import com.lmg.vk.ui.viewmodel.UserConnectionsViewModel

@Composable
fun UserConnectionsScreen(
    userId: Long,
    kind: UserConnectionsKind,
    onBack: () -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenGroup: (Long) -> Unit,
    viewModel: UserConnectionsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val colors = LiquidTheme.colors
    val state by viewModel.state.collectAsState()
    val activeAccountId by com.lmg.vk.engine.backend.MusicAuth.profileId.collectAsState()
    LaunchedEffect(userId, kind, activeAccountId) { viewModel.load(userId, kind, force = true) }

    Box(Modifier.fillMaxSize().background(LiquidSurfaces.sheet(colors.isDark))) {
        when {
            state.isLoading && state.items.isEmpty() -> {
                CircularProgressIndicator(
                    color = colors.iconMuted,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            state.error != null && state.items.isEmpty() -> ConnectionsMessage(
                title = "Couldn't load ${kind.title.lowercase()}",
                message = state.error!!,
                action = "Retry",
                onAction = { viewModel.load(userId, kind, force = true) },
            )
            state.items.isEmpty() -> ConnectionsMessage(
                title = "Nothing here yet",
                message = when (kind) {
                    UserConnectionsKind.MUTUAL -> "You don't have mutual friends with this user."
                    UserConnectionsKind.FOLLOWERS -> "VK did not return public followers."
                    UserConnectionsKind.SUBSCRIPTIONS -> "VK did not return public subscriptions."
                },
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 68.dp, bottom = 120.dp),
            ) {
                item {
                    Text(
                        text = "${state.totalCount} ${kind.title.lowercase()}",
                        fontFamily = VkSansText,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = LiquidMetrics.ScreenPadding, vertical = 10.dp),
                    )
                }
                itemsIndexed(state.items, key = { _, item -> item.stableId }) { index, item ->
                    if (index >= state.items.lastIndex - 4) {
                        LaunchedEffect(state.items.size, state.hasMore) { viewModel.loadMore() }
                    }
                    when (item) {
                        is UserConnectionEntry.User -> ConnectionRow(
                            title = item.value.displayName,
                            subtitle = when {
                                !item.value.isActive -> item.value.deactivated.orEmpty()
                                item.value.isOnline -> "Online"
                                item.value.domain.isNotBlank() -> "vk.com/${item.value.domain}"
                                else -> "id${item.value.id}"
                            },
                            imageUrl = item.value.avatarUrl,
                            isGroup = false,
                            onClick = { onOpenUser(item.value.id) },
                        )
                        is UserConnectionEntry.Group -> ConnectionRow(
                            title = item.value.name.ifBlank { "club${item.value.id}" },
                            subtitle = item.value.membersCount?.let { "$it members" }
                                ?: item.value.typeLabel,
                            imageUrl = item.value.avatarUrl,
                            isGroup = true,
                            onClick = { onOpenGroup(item.value.audioOwnerId) },
                        )
                    }
                }
                if (state.isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.iconMuted, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                state.error?.let { error ->
                    item {
                        Text(
                            text = error,
                            fontFamily = VkSansText,
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidClickable(onClick = viewModel::loadMore)
                                .padding(20.dp),
                        )
                    }
                }
            }
        }

        DetailTopBar(
            title = kind.title,
            showTitle = true,
            isDark = colors.isDark,
            onBack = onBack,
        )
    }
}

@Composable
private fun ConnectionRow(
    title: String,
    subtitle: String,
    imageUrl: String,
    isGroup: Boolean,
    onClick: () -> Unit,
) {
    val colors = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidClickable(onClick = onClick)
            .padding(horizontal = LiquidMetrics.ScreenPadding, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape).background(colors.textTertiary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(imageUrl).crossfade(true).build(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    if (isGroup) LmgGlyphs.Users3Outline28 else LmgGlyphs.UserOutline28,
                    null,
                    tint = colors.iconMuted,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontFamily = VkSansText,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                fontFamily = VkSansText,
                color = colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(LmgGlyphs.ChevronRightOutline24, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ConnectionsMessage(
    title: String,
    message: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LiquidTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(LmgGlyphs.UsersOutline28, null, tint = colors.iconMuted, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, fontFamily = VkSansText, color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(message, fontFamily = VkSansText, color = colors.textSecondary, fontSize = 13.sp)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                action,
                fontFamily = VkSansText,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.textTertiary.copy(alpha = 0.14f))
                    .liquidClickable(onClick = onAction)
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            )
        }
    }
}
