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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
// PersonAddAlt1, а не PersonAdd: этот вариант уже используется в AirPlaySheet,
// то есть заведомо есть в подключённом material-icons-extended.
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lmg.vk.ui.icons.LmgDrawables
import com.lmg.vk.ui.icons.lmgVector
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.Track
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.toTrack
import com.lmg.vk.network.dto.VkFriend
import com.lmg.vk.network.dto.music.AudioPlaylist
import com.lmg.vk.ui.components.DetailTopBar
import com.lmg.vk.ui.components.DetailTrackRow
import com.lmg.vk.ui.components.formatTotalDuration
import com.lmg.vk.ui.glass.AlbumArtImage
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.theme.AppFontFamily
import com.lmg.vk.ui.theme.LiquidMetrics
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.theme.VkSansDisplay
import com.lmg.vk.ui.viewmodel.GroupUiState
import com.lmg.vk.ui.viewmodel.GroupViewModel

/** Акцент кнопок, как в OwnerAudioScreen и остальных музыкальных экранах. */
private val GroupAccent = Color(0xFFFC3C44)

/**
 * Экран сообщества ВКонтакте.
 *
 * Что показывает и откуда данные:
 *  - шапка (обложка/аватар, название, тип, статус, участники) — `groups.getById`
 *    с широким `fields` (см. `VkMethodsRegistry.GROUP_DETAIL_FIELDS`);
 *  - аудиозаписи — `audio.get` по ОТРИЦАТЕЛЬНОМУ owner_id, играются тем же
 *    путём, что и везде (кэш [MusicBackend] → [PlayerController]);
 *  - плейлисты — `audio.getPlaylists`, открываются штатным экраном плейлиста;
 *  - участники — `groups.getMembers`, строкой аватаров;
 *  - описание — поля `description`/`activity` сообщества.
 *
 * Ничего не выдумывается: если VK поля не отдал, блок не рисуется вовсе, а
 * закрытая музыка объясняется текстом, а не показывается пустым списком.
 */
@Composable
fun GroupScreen(
    ownerId: Long,
    onBack: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenMemberProfile: (Long) -> Unit = {},
    viewModel: GroupViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val context = LocalContext.current
    val colors = LiquidTheme.colors
    val isDark = colors.isDark
    val listState = rememberLazyListState()
    val state by viewModel.state.collectAsState()
    val activeAccountId by com.lmg.vk.engine.backend.MusicAuth.profileId.collectAsState()

    val window = com.lmg.vk.ui.rememberWindowInfo()
    val compact = window.useSideBySide

    LaunchedEffect(ownerId, activeAccountId) { viewModel.load(ownerId, force = true) }

    // Треки конвертируются один раз на изменение списка: маппинг заодно кладёт их
    // в кэш бэкенда, без него плеер полез бы за audio.getById.
    val uiTracks: List<Track> = remember(state.tracks) {
        MusicBackend.adoptTracks(state.tracks).map { it.toTrack() }
    }
    val playable = remember(uiTracks) { uiTracks.filter(Track::isAvailable) }

    val showTopTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120
        }
    }

    // Догрузка следующей страницы, когда до конца списка осталось меньше экрана.
    LaunchedEffect(state.ownerId) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to info.totalItemsCount
        }.collect { (last, total) ->
            if (total > 0 && last >= total - 5) viewModel.loadMoreTracks()
        }
    }

    // Фон — лист из общего словаря, как на профиле и артисте.
    Box(modifier = Modifier.fillMaxSize().background(LiquidSurfaces.sheet(colors.isDark))) {
        when {
            // Скелетон вместо голого спиннера: экран сразу занимает ту же форму,
            // что и с данными, поэтому появление контента не «прыгает».
            state.isLoading && state.group == null -> GroupSkeleton(compact = compact)

            state.notFound -> GroupMessage(
                icon = com.lmg.vk.ui.icons.LmgGlyphs.InfoCircleOutline28,
                title = "Сообщество не найдено",
                message = "VK не знает сообщества с id ${-state.ownerId}. Возможно, его удалили.",
            )

            state.error != null && state.group == null -> GroupMessage(
                icon = com.lmg.vk.ui.icons.LmgGlyphs.Users3Outline28,
                title = "Не удалось открыть сообщество",
                message = state.error!!,
                actionLabel = "Повторить",
                onAction = { viewModel.load(ownerId, force = true) },
            )

            else -> {
                val group = state.group
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 640.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(bottom = 140.dp),
                ) {
                    item {
                        GroupHeader(
                            state = state,
                            compact = compact,
                            canPlay = playable.isNotEmpty(),
                            totalDurationMs = playable.sumOf(Track::durationMs),
                            onPlay = { PlayerController.play(context, playable, 0) },
                            onShuffle = { PlayerController.play(context, playable.shuffled(), 0) },
                            onToggleMembership = viewModel::toggleMembership,
                        )
                    }

                    // ── Описание ──
                    // `description` — полный текст, `activity` — однострочная
                    // характеристика. Показываем оба, но не дублируем, если VK
                    // положил в них одно и то же.
                    val about = group?.description?.takeIf(String::isNotBlank)
                    val activity = group?.activity
                        ?.takeIf(String::isNotBlank)
                        ?.takeIf { it != about }
                    if (about != null || activity != null) {
                        item {
                            GroupAboutBlock(about = about, activity = activity)
                        }
                    }

                    // ── Участники ──
                    if (state.members.isNotEmpty()) {
                        item {
                            GroupSectionLabel(
                                state.membersCount
                                    ?.let { "УЧАСТНИКИ • $it" }
                                    ?: "УЧАСТНИКИ",
                            )
                        }
                        item {
                            GroupMembersRow(
                                members = state.members,
                                onOpenMember = onOpenMemberProfile,
                            )
                        }
                    }

                    // ── Плейлисты сообщества ──
                    if (state.playlists.isNotEmpty()) {
                        item { GroupSectionLabel("ПЛЕЙЛИСТЫ • ${state.playlists.size}") }
                        item {
                            GroupPlaylistsRow(
                                playlists = state.playlists,
                                compact = compact,
                                onOpenPlaylist = onOpenPlaylist,
                            )
                        }
                    }
                    state.playlistsError?.let { message ->
                        item { GroupNotice(message) }
                    }

                    // ── Аудиозаписи ──
                    item {
                        GroupSectionLabel(
                            state.tracksTotal
                                ?.takeIf { it > 0 }
                                ?.let { "АУДИОЗАПИСИ • $it" }
                                ?: "АУДИОЗАПИСИ",
                        )
                    }

                    when {
                        state.audioClosed -> item {
                            GroupInlineMessage(
                                icon = com.lmg.vk.ui.icons.LmgGlyphs.LockOutline28,
                                title = "Музыка закрыта",
                                message = "Сообщество не открывает свои аудиозаписи.",
                            )
                        }

                        state.audioError != null && state.tracks.isEmpty() -> item {
                            GroupInlineMessage(
                                icon = com.lmg.vk.ui.icons.LmgGlyphs.MusicNote24,
                                title = "Не удалось загрузить музыку",
                                message = state.audioError!!,
                                actionLabel = "Повторить",
                                onAction = { viewModel.load(ownerId, force = true) },
                            )
                        }

                        // Честная пустота: у сообщества действительно нет аудио.
                        state.audioIsEmpty -> item {
                            GroupInlineMessage(
                                icon = com.lmg.vk.ui.icons.LmgGlyphs.MusicNote24,
                                title = "Нет аудиозаписей",
                                message = "Сообщество не добавило ни одного трека.",
                            )
                        }

                        else -> {
                            itemsIndexed(uiTracks, key = { _, track -> track.id }) { index, track ->
                                DetailTrackRow(
                                    position = index + 1,
                                    title = track.title,
                                    subtitle = track.artist,
                                    durationMs = track.durationMs,
                                    coverUrl = track.coverUrl,
                                    isDark = isDark,
                                    showDivider = index < uiTracks.lastIndex,
                                    enabled = track.isAvailable,
                                    onClick = {
                                        val target = playable.indexOfFirst { it.id == track.id }
                                        if (target >= 0) PlayerController.play(context, playable, target)
                                    },
                                )
                            }
                        }
                    }

                    if (state.isLoadingMoreTracks) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = colors.iconMuted,
                                )
                            }
                        }
                    }

                    // Ошибка догрузки при уже показанных треках — сноской, чтобы
                    // не выкидывать из списка то, что успело прийти.
                    state.audioError?.takeIf { state.tracks.isNotEmpty() }?.let { message ->
                        item { GroupNotice(message) }
                    }
                }
            }
        }

        DetailTopBar(
            title = state.group?.name.orEmpty(),
            showTitle = showTopTitle,
            isDark = isDark,
            onBack = onBack,
        )
    }
}

/**
 * Шапка сообщества — тот же приём, что на профиле, артисте и альбоме: картинка
 * на всю ширину, затемнение, крупное имя, действия капсулами, поверх снизу
 * наезжает лист контента.
 *
 * ЧТО БЕРЁМ ФОНОМ. Сначала широкая обложка (`cover`): у музыкальных сообществ
 * она есть почти всегда и специально нарисована под шапку. Нет обложки — аватар
 * с обрезкой по центру. Нет и его — ровная тёмная плашка, но НЕ выдуманная
 * картинка.
 *
 * Раньше здесь был центрированный кружок 96dp на `settingsBackground`, то есть
 * язык системных настроек, из-за которого экран выпадал из общего ряда.
 */
@Composable
private fun GroupHeader(
    state: GroupUiState,
    compact: Boolean,
    canPlay: Boolean,
    totalDurationMs: Long,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onToggleMembership: () -> Unit,
) {
    val isDark = LiquidTheme.colors.isDark
    val group = state.group
    // Обложка приоритетнее: она широкая и рассчитана на шапку, аватар же
    // квадратный и на всю ширину растягивается с большей обрезкой.
    val backdrop = group?.coverUrl?.takeIf { it.isNotBlank() }
        ?: group?.bigAvatarUrl?.takeIf { it.isNotBlank() }

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(if (compact) 300.dp else 380.dp)) {
            Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                if (backdrop != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(backdrop)
                            .crossfade(true)
                            .build(),
                        contentDescription = group?.name,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFF2A2A2E)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            com.lmg.vk.ui.icons.LmgGlyphs.Users3Outline28,
                            null,
                            tint = Color.White.copy(alpha = 0.30f),
                            modifier = Modifier.size(96.dp),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.35f),
                                0.30f to Color.Transparent,
                                0.60f to Color.Black.copy(alpha = 0.25f),
                                1f to Color.Black.copy(alpha = 0.88f),
                            ),
                        ),
                )
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = group?.name.orEmpty(),
                        color = LiquidSurfaces.onHeaderPrimary,
                        fontFamily = VkSansDisplay,
                        fontSize = if (compact) 28.sp else 36.sp,
                        fontWeight = LiquidMetrics.TitleHugeWeight,
                        letterSpacing = LiquidMetrics.TitleHugeSpacing,
                        lineHeight = if (compact) 32.sp else 40.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (group?.verified == 1) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            lmgVector(LmgDrawables.CheckShieldOutline28),
                            contentDescription = "Verified",
                            tint = Color(0xFF2787F5),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Одна строка фактов вместо трёх абзацев: тип, участники,
                // закрытость и сводка по музыке. Только то, что VK реально
                // прислал — счётчик участников приходит не всегда.
                val facts = buildList {
                    group?.let { add(it.typeLabel) }
                    state.membersCount?.let { add("$it участников") }
                    if (group?.isPrivate == true) add("Закрытое")
                    state.tracksTotal?.takeIf { it > 0 }?.let { add("$it треков") }
                    if (totalDurationMs > 0) add(formatTotalDuration(totalDurationMs))
                    state.playlists.size.takeIf { it > 0 }?.let { add("$it плейлистов") }
                }
                if (facts.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = facts.joinToString(" • "),
                        color = LiquidSurfaces.onHeaderSecondary,
                        fontFamily = AppFontFamily,
                        fontSize = LiquidMetrics.HeaderCaption,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                group?.statusText?.let { status ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = status,
                        color = LiquidSurfaces.onHeaderSecondary,
                        fontFamily = AppFontFamily,
                        fontSize = 12.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GroupActionButton(
                        label = "Слушать",
                        icon = com.lmg.vk.ui.icons.LmgGlyphs.Play28,
                        enabled = canPlay,
                        filled = true,
                        modifier = Modifier.weight(1f),
                        onClick = onPlay,
                    )
                    GroupActionButton(
                        label = "Вперемешку",
                        icon = com.lmg.vk.ui.icons.LmgGlyphs.ShuffleOutline28,
                        enabled = canPlay,
                        filled = false,
                        modifier = Modifier.weight(1f),
                        onClick = onShuffle,
                    )
                }

                // Кнопка подписки показывается ТОЛЬКО когда VK сказал `is_member`:
                // иначе неизвестно, что на ней писать, а угадывать нельзя.
                state.isMember?.let { isMember ->
                    Spacer(Modifier.height(10.dp))
                    GroupMembershipButton(
                        isMember = isMember,
                        isChanging = state.isMembershipChanging,
                        onClick = onToggleMembership,
                    )
                }
                state.membershipError?.let { message ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = message,
                        color = LiquidSurfaces.onHeaderSecondary,
                        fontFamily = AppFontFamily,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        // Верхушка листа наезжает на шапку — та читается подложкой, а не первым
        // элементом списка.
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

/**
 * Кнопка подписки. Стеклянная в обоих состояниях: она лежит на фотографии
 * шапки, где цвета темы не работают. Подписанное состояние отличается значком
 * и текстом, а не заливкой — это не призыв к действию, и выделять его нечем.
 */
@Composable
private fun GroupMembershipButton(
    isMember: Boolean,
    isChanging: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (isChanging) Color.White.copy(alpha = 0.45f) else Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LiquidMetrics.ActionButtonHeight)
            .clip(CircleShape)
            .background(
                if (isChanging) Color.White.copy(alpha = 0.10f) else LiquidSurfaces.glassAction,
            )
            .liquidClickable(
                enabled = !isChanging,
                pressedScale = LiquidMotion.PressButton,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isChanging) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = Color.White.copy(alpha = 0.7f),
            )
        } else {
            Icon(
                imageVector = if (isMember) lmgVector(LmgDrawables.CheckDoubleOutline16) else com.lmg.vk.ui.icons.LmgGlyphs.UsersOutline28,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (isMember) "Вы подписаны" else "Подписаться",
            fontFamily = AppFontFamily,
            color = contentColor,
            fontSize = LiquidMetrics.ActionLabel,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Кнопка действия поверх картинки шапки — контракт артиста: главная сплошная
 * белая (под ней снимок, только плотная заливка гарантирует читаемость),
 * вторая стеклянная, чтобы не спорить с главной за внимание.
 *
 * Цвета темы здесь не годятся: они рассчитаны на лист, а не на кадр.
 */
@Composable
private fun GroupActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    filled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val contentColor = when {
        !enabled -> Color.White.copy(alpha = 0.45f)
        filled -> Color.Black
        else -> Color.White
    }
    Row(
        modifier = modifier
            .height(LiquidMetrics.ActionButtonHeight)
            .shadow(
                elevation = if (filled) LiquidMetrics.ButtonElevation else 2.dp,
                shape = CircleShape,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> Color.White.copy(alpha = 0.10f)
                    filled -> Color.White
                    else -> LiquidSurfaces.glassAction
                },
            )
            .liquidClickable(
                enabled = enabled,
                pressedScale = LiquidMotion.PressButton,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontFamily = AppFontFamily,
            color = contentColor,
            fontSize = LiquidMetrics.ActionLabel,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Описание сообщества. Свёрнуто до нескольких строк: у сообществ описания бывают
 * на несколько экранов, и разворачивать их сразу — значит отодвинуть музыку,
 * ради которой экран и открывают.
 */
@Composable
private fun GroupAboutBlock(about: String?, activity: String?) {
    val colors = LiquidTheme.colors
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        activity?.let {
            Text(
                text = it,
                fontFamily = AppFontFamily,
                color = colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            if (about != null) Spacer(Modifier.height(8.dp))
        }
        about?.let {
            Text(
                text = it,
                fontFamily = AppFontFamily,
                color = colors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_ABOUT_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.liquidClickable { expanded = !expanded },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (expanded) "Свернуть" else "Показать полностью",
                fontFamily = AppFontFamily,
                color = colors.textTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.liquidClickable { expanded = !expanded },
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Участники строкой: тап уводит в аудиозаписи участника — экран музыкальный. */
@Composable
private fun GroupMembersRow(
    members: List<VkFriend>,
    onOpenMember: (Long) -> Unit,
) {
    val colors = LiquidTheme.colors
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
    ) {
        items(members, key = { it.id }) { member ->
            Column(
                modifier = Modifier
                    .width(64.dp)
                    .liquidClickable { onOpenMember(member.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(colors.textTertiary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (member.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = member.avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            com.lmg.vk.ui.icons.LmgGlyphs.UserOutline28,
                            contentDescription = null,
                            tint = colors.iconMuted,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = member.firstName.ifBlank { member.displayName },
                    fontFamily = AppFontFamily,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Плейлисты сообщества — карточками, как в остальных музыкальных разделах. */
@Composable
private fun GroupPlaylistsRow(
    playlists: List<AudioPlaylist>,
    compact: Boolean,
    onOpenPlaylist: (String) -> Unit,
) {
    val colors = LiquidTheme.colors
    val cardSize = if (compact) 108.dp else 130.dp
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
    ) {
        items(playlists, key = { it.fullId }) { playlist ->
            Column(
                modifier = Modifier
                    .width(cardSize)
                    // fullId («owner_id_playlistId») — ровно тот формат, который
                    // разбирает MusicBackend.parsePlaylistId на экране плейлиста.
                    .liquidClickable { onOpenPlaylist(playlist.fullId) },
            ) {
                AlbumArtImage(
                    uri = null,
                    // Обложка плейлиста берётся так же, как в MusicBackend:
                    // сначала `photo`, иначе самый крупный из `thumbs`.
                    coverUrl = playlist.photo?.bestUrl
                        ?: playlist.thumbs?.maxByOrNull { it.width * it.height }?.bestUrl,
                    contentDescription = playlist.title,
                    modifier = Modifier
                        .size(cardSize)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = playlist.title,
                    fontFamily = AppFontFamily,
                    color = colors.textPrimary,
                    fontSize = if (compact) 12.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // Число треков VK отдаёт полем `count`; нулевое не показываем,
                // чтобы не утверждать «0 треков» там, где это просто нет данных.
                playlist.count.takeIf { it > 0 }?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "$it треков",
                        fontFamily = AppFontFamily,
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupSectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = AppFontFamily,
        color = LiquidTheme.colors.sectionLabel,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

/** Короткая сноска-предупреждение внутри списка (ошибка секции). */
@Composable
private fun GroupNotice(message: String) {
    Text(
        text = message,
        fontFamily = AppFontFamily,
        color = LiquidTheme.colors.textSecondary,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

/** Сообщение внутри списка: секция пуста/закрыта, но экран в целом жив. */
@Composable
private fun GroupInlineMessage(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LiquidTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = colors.iconMuted, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            fontFamily = AppFontFamily,
            color = colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = message,
            fontFamily = AppFontFamily,
            color = colors.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            GroupRetryButton(label = actionLabel, onClick = onAction)
        }
    }
}

/** Полноэкранное сообщение: экран открыть не удалось вовсе. */
@Composable
private fun GroupMessage(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LiquidTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = colors.iconMuted, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            fontFamily = AppFontFamily,
            color = colors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            fontFamily = AppFontFamily,
            color = colors.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            GroupRetryButton(label = actionLabel, onClick = onAction)
        }
    }
}

@Composable
private fun GroupRetryButton(label: String, onClick: () -> Unit) {
    val colors = LiquidTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.textTertiary.copy(alpha = 0.14f))
            .liquidClickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(com.lmg.vk.ui.icons.LmgGlyphs.RefreshOutline28, null, tint = GroupAccent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontFamily = AppFontFamily,
            color = GroupAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Скелетон первой загрузки: повторяет геометрию шапки и первых строк, поэтому
 * при появлении данных ничего не «прыгает». Цифр и текстов здесь нет намеренно —
 * плашка не должна выглядеть как реальные данные.
 */
@Composable
private fun GroupSkeleton(compact: Boolean) {
    val colors = LiquidTheme.colors
    val block = colors.textTertiary.copy(alpha = 0.12f)
    val headerHeight = if (compact) 300.dp else 380.dp

    Column(modifier = Modifier.fillMaxSize()) {
        // Геометрия новой шапки: картинка во всю ширину, а имя и кнопки внизу
        // слева. Прежний скелетон повторял центрированный кружок, которого больше
        // нет, и данные при появлении «прыгали».
        Box(modifier = Modifier.fillMaxWidth().height(headerHeight)) {
            Box(Modifier.fillMaxSize().background(block))
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
                Box(Modifier.fillMaxWidth(0.7f).height(if (compact) 28.dp else 36.dp).clip(RoundedCornerShape(8.dp)).background(block))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.45f).height(14.dp).clip(RoundedCornerShape(6.dp)).background(block))
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f).height(LiquidMetrics.ActionButtonHeight).clip(CircleShape).background(block))
                    Box(Modifier.weight(1f).height(LiquidMetrics.ActionButtonHeight).clip(CircleShape).background(block))
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = LiquidMetrics.ScreenPadding)) {
            Spacer(Modifier.height(20.dp))
            repeat(SKELETON_ROWS) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).background(block))
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(5.dp)).background(block))
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth(0.35f).height(11.dp).clip(RoundedCornerShape(5.dp)).background(block))
                    }
                }
            }
        }
    }
}

/** Сколько строк рисует скелетон — примерно экран телефона. */
private const val SKELETON_ROWS = 7

/** До скольких строк свёрнуто описание сообщества. */
private const val COLLAPSED_ABOUT_LINES = 4
