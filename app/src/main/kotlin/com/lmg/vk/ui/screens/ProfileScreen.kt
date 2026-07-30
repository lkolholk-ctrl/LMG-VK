package com.lmg.vk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.data.local.LocalAuthManager
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.theme.AppFontFamily
import com.lmg.vk.ui.theme.LiquidTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AppleRed = Color(0xFFFC3C44)
private val PremiumPurple = Color(0xFF8B5CF6)
private val SurfaceDark = Color(0xFF1C1C1E)
private val SurfaceElevated = Color(0xFF2C2C2E)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit = {},
    onLogout: () -> Unit = {},
    onOpenAuth: () -> Unit = {},
    onOpenStats: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lc = LiquidTheme.colors

    val isLoggedIn by MusicAuth.isLoggedIn.collectAsState()
    val isPremium by MusicAuth.isPremium.collectAsState()
    val userEmail by MusicAuth.userEmail.collectAsState()
    val telegramId by MusicAuth.telegramId.collectAsState()
    val premiumExpiresAt by MusicAuth.premiumExpiresAt.collectAsState()
    val profileName by MusicAuth.profileName.collectAsState()
    val avatarUrl by MusicAuth.avatarUrl.collectAsState()
    val subscription by MusicAuth.subscription.collectAsState()


    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) MusicAuth.fetchUserData()
    }

    // ── Подписки на артистов + регион (всё про подписку живёт в профиле) ──
    var followedArtists by remember {
        mutableStateOf<List<com.lmg.vk.engine.backend.LibraryArtist>>(emptyList())
    }
    var regionInfo by remember {
        mutableStateOf<com.lmg.vk.engine.backend.RegionResponse?>(null)
    }
    var regionExpanded by remember { mutableStateOf(false) }
    var regionBusy by remember { mutableStateOf(false) }
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            followedArtists = runCatching {
                com.lmg.vk.engine.backend.MusicBackend
                    .getLibrarySubscriptions(limit = 50)?.items
            }.getOrNull() ?: emptyList()
            regionInfo = runCatching {
                com.lmg.vk.engine.backend.MusicBackend.getUserRegion()
            }.getOrNull()
        } else {
            followedArtists = emptyList()
            regionInfo = null
        }
    }

    val displayName = when {
        !profileName.isNullOrBlank() -> profileName!!
        userEmail != null -> userEmail!!.substringBefore("@").replaceFirstChar { it.uppercase() }
        telegramId != null -> "Telegram user"
        else -> "Guest"
    }

    // Широкое окно (телефон-альбом ИЛИ планшет). В портрете layout не меняется.
    val win = com.lmg.vk.ui.rememberWindowInfo()
    // Альбом/планшет: делаем всё компактнее (аватар/шрифты/строки ~20-30%),
    // как в LandscapeHome/SideBar. В портрете compact=false → всё как было.
    val compact = win.useSideBySide

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LiquidTheme.colors.settingsBackground)
    ) {
        LazyColumn(
            // В альбоме/на планшете шапку профиля и карточки не растягиваем на всю
            // ширину — ограничиваем 640dp и центрируем. Портрет остаётся как был.
            // ModalBottomSheet (PasswordSheet) — оверлей, его это не трогает.
            modifier = if (win.useSideBySide)
                Modifier.fillMaxHeight().widthIn(max = 640.dp).align(Alignment.TopCenter)
            else Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Status bar spacing ──
            item { Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
            item { Spacer(modifier = Modifier.height(if (compact) 12.dp else 24.dp)) }

            // ═══════════════════════════════════════════════════════════
            //  1. PROFILE HEADER & IDENTITY BLOCK
            // ═══════════════════════════════════════════════════════════
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar — БОЛЬШАЯ круглая (полевой фидбек: «вид аккаунта
                    // с большой аватаркой»).
                    Box(
                        modifier = Modifier
                            .size(if (compact) 92.dp else 132.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (lc.isDark) SurfaceDark else Color(0xFFF2F2F7)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Person,
                                null,
                                tint = lc.iconMuted,
                                modifier = Modifier.size(if (compact) 44.dp else 64.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(if (compact) 12.dp else 18.dp))

                    // Username + Premium Star inline
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = displayName,
                            fontFamily = AppFontFamily,
                            color = lc.textPrimary,
                            fontSize = if (compact) 20.sp else 26.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.02).sp
                        )
                        if (isPremium) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = "Premium",
                                tint = AppleRed,
                                modifier = Modifier.size(if (compact) 15.dp else 18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Premium status text — clean, no background substrate
                    if (isPremium) {
                        val sub = subscription
                        val planLabel = when (sub?.planType) {
                            "family" -> if (sub.isFamilyOwner) "Premium (Family Owner)" else "Premium (Family Member)"
                            "personal" -> "Premium (Personal)"
                            else -> "Premium"
                        }
                        val expiryText = when {
                            !sub?.expiresAtIso.isNullOrBlank() -> {
                                val dateStr = sub!!.expiresAtIso.substringBefore("T")
                                try {
                                    val parts = dateStr.split("-")
                                    "${parts[2]}.${parts[1]}.${parts[0]}"
                                } catch (_: Exception) { dateStr }
                            }
                            premiumExpiresAt > 0 -> SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                                .format(Date(premiumExpiresAt))
                            else -> "Lifetime"
                        }
                        val daysLeftText = if (sub != null && sub.daysLeft > 0) " • ${sub.daysLeft} days left" else ""
                        Text(
                            text = "$planLabel • Until $expiryText$daysLeftText",
                            fontFamily = AppFontFamily,
                            color = lc.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        )
                        // Region line
                        val regionName = sub?.regions?.firstOrNull()?.name ?: "Global"
                        val rawCode = sub?.regions?.firstOrNull()?.code
                        val regionCode = if (rawCode.equals("nz", ignoreCase = true)) "US"
                            else rawCode?.uppercase() ?: "WW"
                        // NZ — аварийное зеркало US (см. регион-селектор ниже):
                        // показываем как America, не раскрывая юзеру фейловер.
                        val regionCodeRaw = sub?.regions?.firstOrNull()?.code
                        val displayRegion = when {
                            regionCodeRaw.equals("nz", ignoreCase = true) ||
                            regionName.equals("США", ignoreCase = true) ||
                            regionName.equals("US", ignoreCase = true) ||
                            regionName.equals("United States", ignoreCase = true) -> "America"
                            else -> regionName
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Region: $displayRegion ($regionCode)",
                            fontFamily = AppFontFamily,
                            color = lc.textSecondary,
                            fontSize = 12.sp,
                            letterSpacing = 0.3.sp
                        )
                    } else {
                        Text(
                            text = "Free Plan",
                            fontFamily = AppFontFamily,
                            color = lc.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(if (compact) 14.dp else 24.dp)) }

            // ═══════════════════════════════════════════════════════════
            //  2. ARTISTS YOU FOLLOW — подписки backend (/library/subscriptions)
            // ═══════════════════════════════════════════════════════════
            if (isLoggedIn && followedArtists.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(if (lc.isDark) SurfaceDark else Color(0xFFF2F2F7))
                            .padding(vertical = 16.dp)
                    ) {
                        Text(
                            text = "ARTISTS YOU FOLLOW",
                            fontFamily = AppFontFamily,
                            color = lc.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(followedArtists.size) { i ->
                                val artist = followedArtists[i]
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(if (compact) 64.dp else 76.dp)
                                        .liquidClickable {
                                            // Тап = радио по артисту (мгновенный старт).
                                            com.lmg.vk.engine.PlayerController
                                                .startArtistWave(context, artist.id, artist.displayName)
                                        }
                                ) {
                                    val img = artist.image ?: artist.cover
                                    Box(
                                        modifier = Modifier
                                            .size(if (compact) 52.dp else 64.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(if (lc.isDark) SurfaceElevated else Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!img.isNullOrBlank()) {
                                            AsyncImage(
                                                model = img,
                                                contentDescription = null,
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                Icons.Rounded.Person, null,
                                                tint = lc.iconMuted,
                                                modifier = Modifier.size(if (compact) 24.dp else 28.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = artist.displayName,
                                        fontFamily = AppFontFamily,
                                        color = lc.textPrimary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // ═══════════════════════════════════════════════════════════
            //  3. REGION — текущий + выбор из доступных (/me/region)
            // ═══════════════════════════════════════════════════════════
            if (isLoggedIn && regionInfo != null) {
                item {
                    val ri = regionInfo!!
                    // NZ у backend — аварийное зеркало US (включают при проблемах с
                    // US-аккаунтом; менеджер: «показывайте us free, разницы нет»).
                    // Юзеру не показываем кухню фейловера — рисуем как United States.
                    fun regionDisplay(code: String, name: String): String =
                        if (code.equals("nz", true)) "United States" else name
                    // Селектор — только то, что доступно партнёрскому ключу
                    // (allowed_by_partner). Пустой список = старый сервер → показываем всё.
                    val selectableRegions = if (ri.allowedByPartner.isEmpty()) ri.available
                        else ri.available.filter { it.code in ri.allowedByPartner }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(if (lc.isDark) SurfaceDark else Color(0xFFF2F2F7))
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (compact) 48.dp else 56.dp)
                                .liquidClickable { regionExpanded = !regionExpanded }
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Region",
                                    fontFamily = AppFontFamily,
                                    color = lc.textPrimary,
                                    fontSize = if (compact) 13.5.sp else 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = ri.available.firstOrNull { it.code == ri.current }
                                        ?.let { regionDisplay(it.code, it.name) }
                                        ?: regionDisplay(ri.current, ri.current.uppercase()),
                                    fontFamily = AppFontFamily,
                                    color = lc.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            if (regionBusy) {
                                Text("…", color = lc.textSecondary, fontSize = 15.sp)
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                                    tint = lc.iconMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (regionExpanded) {
                            for (r in selectableRegions) {
                                val selected = r.code == ri.current
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (compact) 40.dp else 44.dp)
                                        .liquidClickable(enabled = !regionBusy && !selected) {
                                            regionBusy = true
                                            scope.launch {
                                                val ok = runCatching {
                                                    com.lmg.vk.engine.backend.MusicBackend
                                                        .updateUserRegion(r.code)
                                                }.getOrNull() != null
                                                if (ok) {
                                                    regionInfo = runCatching {
                                                        com.lmg.vk.engine.backend.MusicBackend.getUserRegion()
                                                    }.getOrNull() ?: regionInfo
                                                    MusicAuth.fetchUserData()
                                                    // Явный выбор юзера приоритетнее серверного
                                                    // дефолта: ставим ПОСЛЕ fetchUserData (внутри него
                                                    // syncRegionFromServer мог поставить /me/region.current).
                                                    com.lmg.vk.engine.backend.MusicBackend.region = r.code
                                                } else {
                                                    android.widget.Toast.makeText(
                                                        context, "Couldn't switch region",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                                regionBusy = false
                                                regionExpanded = false
                                            }
                                        }
                                        .padding(horizontal = 20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Пометка честно из ДВУХ полей сервера:
                                    // requires_subscription главнее флага free
                                    // (флаг у backend может значить «доступен твоему
                                    // партнёрскому ключу», а не «бесплатен всем»).
                                    val needsSub = r.code in ri.requiresSubscription
                                    Text(
                                        text = regionDisplay(r.code, r.name) + when {
                                            needsSub -> " • premium"
                                            r.free -> " • free"
                                            else -> ""
                                        },
                                        fontFamily = AppFontFamily,
                                        color = if (selected) AppleRed else lc.textPrimary,
                                        fontSize = if (compact) 13.sp else 14.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (selected) {
                                        Icon(
                                            Icons.Rounded.Star, null,
                                            tint = AppleRed,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // ── Listening Stats ──
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (lc.isDark) SurfaceDark else Color(0xFFF2F2F7))
                ) {
                    SettingRowNavigable(
                        icon = Icons.Rounded.BarChart,
                        label = "Listening Stats",
                        value = "Your top songs & artists",
                        compact = compact,
                        onClick = onOpenStats
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── ACCOUNT ──
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (lc.isDark) SurfaceDark else Color(0xFFF2F2F7))
                ) {
                    if (isLoggedIn) {
                        Column {
                            SettingRowAction(
                                icon = Icons.AutoMirrored.Rounded.ExitToApp,
                                label = "Sign Out",
                                tint = AppleRed,
                                compact = compact,
                                onClick = {
                                    LocalAuthManager.logout()
                                    MusicAuth.logout()
                                    onLogout()
                                }
                            )
                        }
                    } else {
                        SettingRowNavigable(
                            icon = Icons.Rounded.Person,
                            label = "Sign In",
                            value = "Connect your account",
                            compact = compact,
                            onClick = onOpenAuth
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(if (compact) 20.dp else 32.dp)) }

            // ── Footer ──
            item {
                Text(
                    text = "LMG VK • ${com.lmg.vk.BuildConfig.VERSION_NAME}",
                    fontFamily = AppFontFamily,
                    color = lc.textTertiary,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
