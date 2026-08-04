package com.lmg.vk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lmg.vk.data.local.db.AppDatabase
import com.lmg.vk.data.local.db.FavoriteTrackDatabase
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.engine.PlaylistManager
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.theme.AppFontFamily
import com.lmg.vk.ui.theme.LiquidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DestructiveRed = Color(0xFFFC3C44)
private val ProfileSurfaceDark = Color(0xFF1C1C1E)
private val ProfileSurfaceLight = Color(0xFFF2F2F7)

private data class ProfileLibrarySummary(
    val favorites: Int = 0,
    val downloads: Int = 0,
    val localTracks: Int = 0,
    val plays: Int = 0,
    val listenedMs: Long = 0L,
    val lastPlaybackAt: Long? = null,
    val lastPlaybackSource: String? = null,
)

/**
 * VK account screen. Profile identity is populated only from the recovered
 * `users.get` contract; no LMG/third-party subscription or region facade is used.
 */
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit = {},
    onLogout: () -> Unit = {},
    onOpenAuth: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LiquidTheme.colors
    val isLoggedIn by MusicAuth.isLoggedIn.collectAsState()
    val profileName by MusicAuth.profileName.collectAsState()
    val avatarUrl by MusicAuth.avatarUrl.collectAsState()
    val profileId by MusicAuth.profileId.collectAsState()
    val profileDomain by MusicAuth.profileDomain.collectAsState()
    val isRefreshing by MusicAuth.isProfileRefreshing.collectAsState()
    val sessionExpiresAt by MusicAuth.profileSessionExpiresAt.collectAsState()
    val playlists by PlaylistManager.playlists.collectAsState()
    var showSignOutConfirmation by remember { mutableStateOf(false) }
    var librarySummary by remember { mutableStateOf(ProfileLibrarySummary()) }

    suspend fun loadLibrarySummary(): ProfileLibrarySummary = withContext(Dispatchers.IO) {
        val appDatabase = AppDatabase.getInstance(context)
        val favoritesDatabase = FavoriteTrackDatabase.getInstance(context)
        favoritesDatabase.loadAsync()
        val history = appDatabase.playbackHistoryDao()
        val lastPlayback = history.getRecentHistory(limit = 1).firstOrNull()
        ProfileLibrarySummary(
            favorites = favoritesDatabase.getAllFavorites().size,
            downloads = favoritesDatabase.getDownloadedTracks().size,
            localTracks = appDatabase.localTracksDao().count(),
            plays = history.getTotalPlayEvents(),
            listenedMs = history.getTotalListenedMs(),
            lastPlaybackAt = lastPlayback?.timestamp,
            lastPlaybackSource = lastPlayback?.source,
        )
    }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) MusicAuth.fetchUserData()
    }
    LaunchedEffect(context) {
        librarySummary = loadLibrarySummary()
    }

    val displayName = profileName?.takeIf(String::isNotBlank)
        ?: if (isLoggedIn) "VK account" else "Guest"
    val accountSubtitle = when {
        !profileDomain.isNullOrBlank() -> "vk.com/$profileDomain"
        profileId != null -> "VK ID $profileId"
        isLoggedIn -> "VK account"
        else -> "Sign in to restore your VK library"
    }
    val window = com.lmg.vk.ui.rememberWindowInfo()
    val compact = window.useSideBySide
    val surface = if (colors.isDark) ProfileSurfaceDark else ProfileSurfaceLight

    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmation = false },
            title = { Text("Sign out of VK?") },
            text = { Text("Your encrypted local VK session will be removed from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirmation = false
                        MusicAuth.logout()
                        onLogout()
                    },
                ) { Text("Sign out", color = DestructiveRed) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmation = false }) { Text("Cancel") }
            },
        )
    }

    Box(
        modifier = Modifier.fillMaxSize().background(colors.settingsBackground),
    ) {
        LazyColumn(
            modifier = if (window.useSideBySide) {
                Modifier.fillMaxHeight().widthIn(max = 640.dp).align(Alignment.TopCenter)
            } else {
                Modifier.fillMaxSize()
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
            item { Spacer(Modifier.height(if (compact) 12.dp else 24.dp)) }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (compact) 92.dp else 132.dp)
                            .clip(CircleShape)
                            .background(surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = colors.iconMuted,
                                modifier = Modifier.size(if (compact) 44.dp else 64.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
                    Text(
                        text = displayName,
                        fontFamily = AppFontFamily,
                        color = colors.textPrimary,
                        fontSize = if (compact) 20.sp else 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = accountSubtitle,
                        fontFamily = AppFontFamily,
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                    )
                }
            }

            item { Spacer(Modifier.height(if (compact) 16.dp else 24.dp)) }

            if (isLoggedIn) {
                item {
                    ProfileCard(surface = surface) {
                        ProfileInfoRow(
                            icon = Icons.Rounded.Person,
                            label = "VK ID",
                            value = profileId?.toString() ?: "Loading account…",
                            compact = compact,
                        )
                        if (!profileDomain.isNullOrBlank()) {
                            ProfileDivider()
                            ProfileInfoRow(
                                icon = Icons.Rounded.Person,
                                label = "Profile address",
                                value = "vk.com/$profileDomain",
                                compact = compact,
                            )
                        }
                        ProfileDivider()
                        ProfileInfoRow(
                            icon = Icons.Rounded.Refresh,
                            label = "VK session",
                            value = formatSessionStatus(sessionExpiresAt),
                            compact = compact,
                        )
                        ProfileDivider()
                        ProfileInfoRow(
                            icon = Icons.Rounded.History,
                            label = "Last activity",
                            value = formatLastPlayback(
                                librarySummary.lastPlaybackAt,
                                librarySummary.lastPlaybackSource,
                            ),
                            compact = compact,
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            if (isLoggedIn) {
                item {
                    ProfileCard(surface = surface) {
                        ProfileSectionLabel("YOUR LIBRARY")
                        ProfileMetricsRow(
                            firstValue = librarySummary.favorites.toString(),
                            firstLabel = "Favorites",
                            secondValue = playlists.size.toString(),
                            secondLabel = "Playlists",
                            compact = compact,
                        )
                        ProfileDivider()
                        ProfileMetricsRow(
                            firstValue = librarySummary.downloads.toString(),
                            firstLabel = "Downloads",
                            secondValue = librarySummary.localTracks.toString(),
                            secondLabel = "On device",
                            compact = compact,
                        )
                        ProfileDivider()
                        ProfileMetricsRow(
                            firstValue = formatProfileDuration(librarySummary.listenedMs),
                            firstLabel = "Listened",
                            secondValue = librarySummary.plays.toString(),
                            secondLabel = "Plays",
                            compact = compact,
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            item {
                ProfileCard(surface = surface) {
                    if (isLoggedIn) {
                        ProfileNavigationRow(
                            icon = Icons.Rounded.Refresh,
                            label = if (isRefreshing) "Refreshing profile" else "Refresh profile",
                            value = "Fetch current details from VK",
                            compact = compact,
                            enabled = !isRefreshing,
                            loading = isRefreshing,
                            onClick = {
                                scope.launch {
                                    val refreshed = MusicAuth.fetchUserData()
                                    librarySummary = loadLibrarySummary()
                                    if (!refreshed) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Couldn't refresh VK profile",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            },
                        )
                        ProfileDivider()
                        if (!profileDomain.isNullOrBlank()) {
                            ProfileNavigationRow(
                                icon = Icons.Rounded.Person,
                                label = "Copy VK profile link",
                                value = "vk.com/$profileDomain",
                                compact = compact,
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                        as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(
                                        android.content.ClipData.newPlainText(
                                            "vk_profile_link",
                                            "https://vk.com/$profileDomain",
                                        ),
                                    )
                                    android.widget.Toast.makeText(
                                        context,
                                        "VK profile link copied",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
                            ProfileDivider()
                        }
                        ProfileNavigationRow(
                            icon = Icons.Rounded.QueueMusic,
                            label = "My Library",
                            value = "Favorites, playlists & downloads",
                            compact = compact,
                            onClick = onOpenLibrary,
                        )
                        ProfileDivider()
                        ProfileNavigationRow(
                            icon = Icons.Rounded.BarChart,
                            label = "Listening Stats",
                            value = "Your top songs & artists",
                            compact = compact,
                            onClick = onOpenStats,
                        )
                        ProfileDivider()
                        ProfileNavigationRow(
                            icon = Icons.Rounded.Settings,
                            label = "Settings",
                            value = "Playback, appearance & data",
                            compact = compact,
                            onClick = onOpenSettings,
                        )
                        ProfileDivider()
                        ProfileActionRow(
                            icon = Icons.AutoMirrored.Rounded.ExitToApp,
                            label = "Sign Out",
                            compact = compact,
                            onClick = { showSignOutConfirmation = true },
                        )
                    } else {
                        ProfileNavigationRow(
                            icon = Icons.Rounded.Person,
                            label = "Sign In",
                            value = "Connect your VK account",
                            compact = compact,
                            onClick = onOpenAuth,
                        )
                        ProfileDivider()
                        ProfileNavigationRow(
                            icon = Icons.Rounded.Settings,
                            label = "Settings",
                            value = "Playback, appearance & data",
                            compact = compact,
                            onClick = onOpenSettings,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(if (compact) 20.dp else 32.dp)) }
            item {
                Text(
                    text = "LMG VK • ${com.lmg.vk.BuildConfig.VERSION_NAME}",
                    fontFamily = AppFontFamily,
                    color = colors.textTertiary,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ProfileCard(surface: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(surface),
        content = content,
    )
}

@Composable
private fun ProfileInfoRow(icon: ImageVector, label: String, value: String, compact: Boolean) {
    val colors = LiquidTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = if (compact) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontFamily = AppFontFamily, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(value, fontFamily = AppFontFamily, color = colors.textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ProfileSectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = AppFontFamily,
        color = LiquidTheme.colors.textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun ProfileMetricsRow(
    firstValue: String,
    firstLabel: String,
    secondValue: String,
    secondLabel: String,
    compact: Boolean,
) {
    val colors = LiquidTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = if (compact) 10.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProfileMetric(firstValue, firstLabel, Modifier.weight(1f), colors)
        ProfileMetric(secondValue, secondLabel, Modifier.weight(1f), colors)
    }
}

@Composable
private fun ProfileMetric(value: String, label: String, modifier: Modifier, colors: com.lmg.vk.ui.theme.LiquidColors) {
    Column(modifier = modifier) {
        Text(value, fontFamily = AppFontFamily, color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(label, fontFamily = AppFontFamily, color = colors.textSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun ProfileNavigationRow(
    icon: ImageVector,
    label: String,
    value: String,
    compact: Boolean,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = if (compact) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontFamily = AppFontFamily, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(value, fontFamily = AppFontFamily, color = colors.textSecondary, fontSize = 12.sp)
        }
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.iconMuted)
        } else {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ProfileActionRow(icon: ImageVector, label: String, compact: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidClickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = if (compact) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = DestructiveRed, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            fontFamily = AppFontFamily,
            color = DestructiveRed,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ProfileDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(start = 52.dp)
            .background(LiquidTheme.colors.textTertiary.copy(alpha = 0.12f)),
    )
}

private fun formatProfileDuration(durationMs: Long): String {
    val totalMinutes = durationMs.coerceAtLeast(0L) / 60_000L
    return when {
        totalMinutes >= 60 -> "${totalMinutes / 60}h ${totalMinutes % 60}m"
        totalMinutes > 0 -> "${totalMinutes}m"
        else -> "0m"
    }
}

private fun formatSessionStatus(expiresAtSeconds: Long?): String {
    if (expiresAtSeconds == null) return "Active • no fixed expiry"
    val minutesLeft = expiresAtSeconds - (System.currentTimeMillis() / 1_000L)
    return when {
        minutesLeft <= 0L -> "Expired • refresh on next VK request"
        minutesLeft < 3_600L -> "Active • expires in ${minutesLeft / 60}m"
        else -> "Active • expires in ${minutesLeft / 3_600}h"
    }
}

private fun formatLastPlayback(timestampMs: Long?, source: String?): String {
    if (timestampMs == null || timestampMs <= 0L) return "No listening events yet"
    val secondsAgo = ((System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)) / 1_000L
    val relative = when {
        secondsAgo < 60L -> "just now"
        secondsAgo < 3_600L -> "${secondsAgo / 60}m ago"
        secondsAgo < 86_400L -> "${secondsAgo / 3_600}h ago"
        else -> "${secondsAgo / 86_400}d ago"
    }
    val sourceLabel = source?.replaceFirstChar { it.uppercase() }?.takeIf(String::isNotBlank)
    return listOfNotNull(sourceLabel, relative).joinToString(" • ")
}
