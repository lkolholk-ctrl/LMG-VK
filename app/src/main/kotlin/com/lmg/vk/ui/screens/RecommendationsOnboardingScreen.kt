package com.lmg.vk.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.ui.glass.AlbumArtImage
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.rememberWindowInfo
import com.lmg.vk.ui.theme.AppFontFamily
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.viewmodel.OnboardingArtist
import com.lmg.vk.ui.viewmodel.OnboardingSubmitState
import com.lmg.vk.ui.viewmodel.OnboardingUiState
import com.lmg.vk.ui.viewmodel.OnboardingViewModel

/**
 * Онбординг музыкальных рекомендаций — порт экрана VK X `C14197e`
 * (спека `docs/vkx-port/01-music.md` §11).
 *
 * Пользователь отмечает исполнителей, которых слушает; выбор уходит в
 * `audio.finishRecomsOnboarding`, после чего ВКонтакте перестраивает
 * персональные рекомендации на своей стороне.
 *
 * Как в оригинале: пустая строка поиска — подсказки от
 * `audio.recommendationsOnboarding`, непустая — живой поиск
 * `audio.searchArtists`; шкала прогресса заполняется по 0.2 на исполнителя, а
 * кнопка «Готово» открывается от пяти отмеченных (`C13752e.java:668`, `:721`).
 *
 * Списка артистов и жанров в коде нет вовсе — всё приходит от VK. Если VK не дал
 * ничего, экран говорит об этом прямо, а не показывает выдуманные карточки.
 *
 * ОТСТУПЛЕНИЕ ОТ VK X: оригинал — обязательный шаг регистрации на весь экран с
 * пропуском наверху. Здесь это добровольная настройка, открываемая из Настроек,
 * поэтому вместо «Пропустить» — обычная кнопка «Назад», а после успешной
 * отправки экран закрывается сам.
 */
@Composable
fun RecommendationsOnboardingScreen(
    onBack: () -> Unit = {},
) {
    val lc = LiquidTheme.colors
    val compact = rememberWindowInfo().useSideBySide
    val sidePad = 20.dp

    val viewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    // VK принял выбор — задерживаться на экране незачем, закрываемся.
    LaunchedEffect(state.submit) {
        if (state.submit is OnboardingSubmitState.Done) onBack()
    }

    Box(modifier = Modifier.fillMaxSize().background(lc.settingsBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(modifier = Modifier.height(12.dp))

            // ── Заголовок ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = sidePad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 34.dp else 40.dp)
                        .clip(CircleShape)
                        .background(if (lc.isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7))
                        .liquidClickable(pressedScale = LiquidMotion.PressIcon) { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        tint = lc.iconDefault,
                        modifier = Modifier.size(if (compact) 18.dp else 22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(if (compact) 12.dp else 16.dp))
                Text(
                    text = "Настроить рекомендации",
                    color = lc.textPrimary,
                    fontSize = if (compact) 20.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AppFontFamily,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Отметьте исполнителей, которых слушаете — ВКонтакте " +
                    "подстроит под них рекомендации",
                color = lc.textSecondary,
                fontSize = if (compact) 12.sp else 13.sp,
                fontFamily = AppFontFamily,
                modifier = Modifier.padding(horizontal = sidePad),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── Прогресс выбора (шкала оригинала) ──
            OnboardingProgress(state = state, compact = compact, sidePad = sidePad)

            Spacer(modifier = Modifier.height(14.dp))

            // ── Поиск исполнителей ──
            OnboardingSearchField(
                query = state.query,
                compact = compact,
                sidePad = sidePad,
                onQueryChange = viewModel::onQueryChange,
                onClear = viewModel::clearQuery,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Box(modifier = Modifier.weight(1f)) {
                when {
                    // Первая загрузка: сетка-скелетон вместо пустоты.
                    state.isLoading && state.artists.isEmpty() ->
                        OnboardingSkeleton(compact = compact, sidePad = sidePad)

                    state.error != null && state.artists.isEmpty() -> OnboardingLoadError(
                        message = state.error.orEmpty(),
                        searching = state.isSearching,
                        compact = compact,
                        sidePad = sidePad,
                        onRetry = viewModel::retry,
                    )

                    // Ответ пришёл, но исполнителей нет — так и говорим.
                    state.isEmpty -> OnboardingEmpty(
                        searching = state.isSearching,
                        query = state.query,
                        compact = compact,
                        onRetry = viewModel::retry,
                    )

                    else -> LazyVerticalGrid(
                        columns = if (compact) {
                            GridCells.Adaptive(minSize = 150.dp)
                        } else {
                            GridCells.Fixed(3)
                        },
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = sidePad,
                            end = sidePad,
                            bottom = 24.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.artists, key = { it.id }) { artist ->
                            ArtistPickCard(
                                artist = artist,
                                selected = artist.id in state.selectedIds,
                                compact = compact,
                                onClick = { viewModel.toggle(artist.id) },
                            )
                        }
                    }
                }
            }

            // ── Ошибка отправки + кнопка «Готово» ──
            (state.submit as? OnboardingSubmitState.Failed)?.let { failed ->
                OnboardingSubmitError(
                    message = failed.message,
                    compact = compact,
                    sidePad = sidePad,
                    onDismiss = viewModel::dismissSubmitError,
                )
            }

            FinishButton(
                state = state,
                compact = compact,
                sidePad = sidePad,
                onFinish = viewModel::finish,
            )

            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Шкала выбора. Заполнение — `min(1, выбрано * 0.2)`, ровно как в оригинале;
 * подпись объясняет, сколько ещё нужно, чтобы кнопка открылась.
 */
@Composable
private fun OnboardingProgress(
    state: OnboardingUiState,
    compact: Boolean,
    sidePad: androidx.compose.ui.unit.Dp,
) {
    val lc = LiquidTheme.colors
    val progress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = LiquidMotion.snappy(),
        label = "onboardingProgress",
    )

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = sidePad)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (lc.isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
        ) {
            // fillMaxWidth(0f) у Compose даёт нулевую ширину — отдельная ветка
            // не нужна, но при нуле полоску вообще не рисуем, чтобы не мигала.
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(lc.accent),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (state.remaining == 0) {
                "Выбрано: ${state.selectedIds.size}"
            } else {
                "Выбрано: ${state.selectedIds.size} — ещё ${state.remaining} " +
                    "до отправки"
            },
            color = lc.textSecondary,
            fontSize = if (compact) 11.sp else 12.sp,
            fontFamily = AppFontFamily,
        )
    }
}

/** Поле поиска: пустое — подсказки VK, непустое — `audio.searchArtists`. */
@Composable
private fun OnboardingSearchField(
    query: String,
    compact: Boolean,
    sidePad: androidx.compose.ui.unit.Dp,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val lc = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sidePad)
            .height(if (compact) 40.dp else 46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (lc.isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = lc.textTertiary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(
                    color = lc.textPrimary,
                    fontSize = if (compact) 14.sp else 15.sp,
                    fontFamily = AppFontFamily,
                ),
                singleLine = true,
                cursorBrush = SolidColor(lc.accent),
                modifier = Modifier.fillMaxWidth(),
            )
            if (query.isEmpty()) {
                Text(
                    text = "Найти исполнителя",
                    color = lc.textTertiary,
                    fontSize = if (compact) 14.sp else 15.sp,
                    fontFamily = AppFontFamily,
                )
            }
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = null,
                tint = lc.textTertiary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onClear),
            )
        }
    }
}

/** Карточка исполнителя. Обложка и жанры — только из ответа VK. */
@Composable
private fun ArtistPickCard(
    artist: OnboardingArtist,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val lc = LiquidTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (lc.isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7))
            .liquidClickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            AlbumArtImage(
                uri = null,
                coverUrl = artist.coverUrl,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
            )
            // Отметка выбора поверх обложки — как в оригинале, где выбранная
            // карточка притемняется и получает галочку.
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(lc.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name,
            color = lc.textPrimary,
            fontSize = if (compact) 13.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = AppFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Жанры показываем, только если VK их прислал.
        if (artist.genres.isNotEmpty()) {
            Text(
                text = artist.genres.joinToString(" · "),
                color = lc.textSecondary,
                fontSize = if (compact) 11.sp else 12.sp,
                fontFamily = AppFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Кнопка отправки. До пяти отмеченных исполнителей неактивна. */
@Composable
private fun FinishButton(
    state: OnboardingUiState,
    compact: Boolean,
    sidePad: androidx.compose.ui.unit.Dp,
    onFinish: () -> Unit,
) {
    val lc = LiquidTheme.colors
    val enabled = state.canFinish
    val sending = state.submit is OnboardingSubmitState.InProgress

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sidePad, vertical = 8.dp)
            .height(if (compact) 44.dp else 50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled) lc.accent else lc.accent.copy(alpha = 0.22f),
            )
            .liquidClickable(enabled = enabled && !sending) { onFinish() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (sending) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = if (enabled) {
                    "Готово"
                } else {
                    "Выберите ещё ${state.remaining}"
                },
                color = if (enabled) Color.White else lc.textSecondary,
                fontSize = if (compact) 14.sp else 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = AppFontFamily,
            )
        }
    }
}

/** Скелетон первой загрузки. */
@Composable
private fun OnboardingSkeleton(compact: Boolean, sidePad: androidx.compose.ui.unit.Dp) {
    val lc = LiquidTheme.colors
    LazyVerticalGrid(
        columns = if (compact) GridCells.Adaptive(minSize = 150.dp) else GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = sidePad, end = sidePad, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items((0 until 9).toList()) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.82f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (lc.isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)),
                contentAlignment = Alignment.Center,
            ) {
                if (index == 0) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = lc.accent,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

/** VK ответил, но исполнителей нет. Честный текст вместо пустого экрана. */
@Composable
private fun OnboardingEmpty(
    searching: Boolean,
    query: String,
    compact: Boolean,
    onRetry: () -> Unit,
) {
    val lc = LiquidTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (searching) {
                "По запросу «$query» исполнителей не нашлось"
            } else {
                "ВКонтакте не предложил исполнителей"
            },
            color = lc.textPrimary,
            fontSize = if (compact) 15.sp else 17.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = AppFontFamily,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (searching) {
                "Попробуйте изменить запрос."
            } else {
                "Найдите исполнителей через поиск выше — выбор всё равно " +
                    "уйдёт в рекомендации."
            },
            color = lc.textSecondary,
            fontSize = if (compact) 12.sp else 13.sp,
            fontFamily = AppFontFamily,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (!searching) {
            RetryChip(compact = compact, onRetry = onRetry)
        }
    }
}

/** Ошибка загрузки списка с возможностью повторить. */
@Composable
private fun OnboardingLoadError(
    message: String,
    searching: Boolean,
    compact: Boolean,
    sidePad: androidx.compose.ui.unit.Dp,
    onRetry: () -> Unit,
) {
    val lc = LiquidTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sidePad)
            .clip(RoundedCornerShape(18.dp))
            .background(if (lc.isDark) Color(0xFF1D1D1F) else Color(0xFFF2F2F7))
            .padding(18.dp),
    ) {
        Text(
            text = if (searching) {
                "Поиск исполнителей не удался"
            } else {
                "Не удалось загрузить исполнителей"
            },
            color = lc.textPrimary,
            fontSize = if (compact) 15.sp else 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = AppFontFamily,
        )
        Text(
            text = message,
            color = lc.textSecondary,
            fontSize = if (compact) 12.sp else 13.sp,
            fontFamily = AppFontFamily,
            modifier = Modifier.padding(top = 6.dp),
        )
        RetryChip(compact = compact, onRetry = onRetry)
    }
}

/** Ошибка отправки выбора — выбор при этом остаётся на экране. */
@Composable
private fun OnboardingSubmitError(
    message: String,
    compact: Boolean,
    sidePad: androidx.compose.ui.unit.Dp,
    onDismiss: () -> Unit,
) {
    val lc = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sidePad)
            .clip(RoundedCornerShape(12.dp))
            .background(lc.accentRed.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = lc.textPrimary,
            fontSize = if (compact) 12.sp else 13.sp,
            fontFamily = AppFontFamily,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = null,
            tint = lc.textSecondary,
            modifier = Modifier
                .size(18.dp)
                .clickable(onClick = onDismiss),
        )
    }
}

@Composable
private fun RetryChip(compact: Boolean, onRetry: () -> Unit) {
    val lc = LiquidTheme.colors
    Row(
        modifier = Modifier
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(lc.accent.copy(alpha = 0.16f))
            .clickable(onClick = onRetry)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = null,
            tint = lc.accent,
            modifier = Modifier.size(17.dp),
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = "Повторить",
            color = lc.accent,
            fontSize = if (compact) 12.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = AppFontFamily,
        )
    }
}
