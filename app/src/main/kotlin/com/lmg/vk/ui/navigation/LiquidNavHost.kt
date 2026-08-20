package com.lmg.vk.ui.navigation

import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.lmg.vk.ui.screens.SettingsScreen
import com.lmg.vk.ui.screens.AlbumDetailScreen
import com.lmg.vk.ui.screens.ArtistDetailScreen
import com.lmg.vk.ui.screens.LibraryScreen
import com.lmg.vk.ui.screens.LocalAlbumDetailScreen
import com.lmg.vk.ui.screens.LocalArtistDetailScreen
import com.lmg.vk.ui.screens.LocalLibraryScreen
import com.lmg.vk.ui.screens.NewScreen
import com.lmg.vk.ui.screens.OwnerAudioRoute
import com.lmg.vk.ui.screens.PlaylistDetailScreen
import com.lmg.vk.ui.screens.RecommendationsOnboardingScreen
import com.lmg.vk.ui.screens.SearchScreen
import com.lmg.vk.ui.screens.SnippetsScreen
import com.lmg.vk.ui.screens.WaveHomeScreen
import com.lmg.vk.ui.screens.YearRecapScreen
import com.lmg.vk.ui.theme.ForceDarkContent

/**
 * Единый NavHost приложения (батч 15). Четыре вложенных графа — по одному на
 * нижнюю вкладку (Волна/Библиотека/New/Настройки). За счёт вложенности каждая
 * вкладка держит СВОЙ бэкстек; переключение вкладок в [BottomBar] делает
 * navigate с saveState/restoreState, поэтому позиция и стек вкладки сохраняются.
 *
 * Экраны-детали (альбом/артист/плейлист) регистрируются внутри графа каждой
 * вкладки с префиксом её тега — так они попадают в бэкстек ИМЕННО этой вкладки.
 *
 * Оверлеи (плеер, профиль, авторизация, редакторы, диалоги) живут
 * НАД NavHost в [AppRoot] и дёргаются через колбэки-параметры.
 */
@Composable
fun LiquidNavHost(
    navController: NavHostController,
    backdrop: LayerBackdrop,
    waveAnimationsActive: Boolean,
    onOpenPlayer: () -> Unit,
    onOpenAuth: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAccounts: () -> Unit = {},
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        route = "root",
        startDestination = NavRoutes.GRAPH_WAVE,
        modifier = modifier,
        // Пуш детали/поиска — выезд справа; возврат — уезд вправо. Тот же язык
        // движения, что был на прежних AnimatedVisibility деталей.
        enterTransition = {
            slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 300f)) { it } + fadeIn(tween(200))
        },
        exitTransition = {
            slideOutHorizontally(spring(dampingRatio = 0.9f, stiffness = 350f)) { -it / 6 } + fadeOut(tween(150))
        },
        popEnterTransition = {
            slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 300f)) { -it / 6 } + fadeIn(tween(200))
        },
        popExitTransition = {
            slideOutHorizontally(spring(dampingRatio = 0.9f, stiffness = 350f)) { it } + fadeOut(tween(150))
        }
    ) {
        // ═══════════ Граф ВОЛНЫ ═══════════
        navigation(startDestination = NavRoutes.WAVE_HOME, route = NavRoutes.GRAPH_WAVE) {
            composable(NavRoutes.WAVE_HOME) { entry ->
                // This VM belongs to this destination's NavBackStackEntry. Android now
                // clears it when the entry is removed instead of leaving a remembered VM alive.
                val homeViewModel = ViewModelProvider(entry)[com.lmg.vk.ui.viewmodel.HomeViewModel::class.java]
                // Волна (портрет) всегда тёмная — эффекты/дым завязаны на тёмный
                // фон. В широком окне (телефон-альбом/планшет) вместо дыма —
                // карточная LandscapeHome, у неё ауры нет, поэтому НЕ форсим тёмную:
                // она должна следовать теме приложения, как и сайдбар слева.
                val win = com.lmg.vk.ui.rememberWindowInfo()
                val waveHome: @Composable () -> Unit = {
                    WaveHomeScreen(
                        viewModel = homeViewModel,
                        onNavigateToSearch = onOpenSearch,
                        onOpenPlayer = onOpenPlayer,
                        onNavigateToAlbum = { navController.navigate(NavRoutes.album(NavRoutes.TAB_WAVE, it)) },
                        onNavigateToArtist = { navController.navigate(NavRoutes.artist(NavRoutes.TAB_WAVE, it)) },
                        onNavigateToPlaylist = { navController.navigate(NavRoutes.playlist(NavRoutes.TAB_WAVE, it)) },
                        onOpenAuth = onOpenAuth,
                        onOpenProfile = onOpenProfile,
                        onOpenAccounts = onOpenAccounts,
                        animationsActive = waveAnimationsActive
                    )
                }
                if (win.useSideBySide) waveHome() else ForceDarkContent { waveHome() }
            }
            composable(NavRoutes.WAVE_SEARCH) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToAlbum = { navController.navigate(NavRoutes.album(NavRoutes.TAB_WAVE, it)) },
                    onNavigateToArtist = { navController.navigate(NavRoutes.artist(NavRoutes.TAB_WAVE, it)) },
                    onOpenPlayer = onOpenPlayer,
                    bottomContentPadding = 178.dp,
                )
            }
            musicDetailDestinations(NavRoutes.TAB_WAVE, navController)
        }

        // ═══════════ Граф БИБЛИОТЕКИ ═══════════
        navigation(startDestination = NavRoutes.LIBRARY_HOME, route = NavRoutes.GRAPH_LIBRARY) {
            composable(NavRoutes.LIBRARY_HOME) {
                LibraryScreen(
                    onNavigateToAlbum = { navController.navigate(NavRoutes.album(NavRoutes.TAB_LIBRARY, it)) },
                    onNavigateToArtist = { navController.navigate(NavRoutes.artist(NavRoutes.TAB_LIBRARY, it)) },
                    onOpenPlaylist = { navController.navigate(NavRoutes.playlist(NavRoutes.TAB_LIBRARY, it)) },
                    onOpenLocalLibrary = { navController.navigate(NavRoutes.LOCAL_LIBRARY) },
                    onOpenDownloads = { navController.navigate(NavRoutes.DOWNLOADS) },
                    backdrop = backdrop
                )
            }
            musicDetailDestinations(NavRoutes.TAB_LIBRARY, navController)


            // Экран «Загрузки» — скачанное на устройство. В графе Библиотеки:
            // вход только оттуда, и бэкстек должен оставаться внутри вкладки.
            composable(NavRoutes.DOWNLOADS) {
                com.lmg.vk.ui.screens.DownloadsScreen(onBack = { navController.popBackStack() })
            }

            composable(NavRoutes.LOCAL_LIBRARY) {
                LocalLibraryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { navController.navigate(NavRoutes.localArtist(it)) },
                    onOpenAlbum = { id, name -> navController.navigate(NavRoutes.localAlbum(id, name)) }
                )
            }

            // «Итоги года» ВКонтакте. Живёт в графе Библиотеки, потому что вход
            // на него — из локальной статистики прослушивания.
            composable(NavRoutes.YEAR_RECAP) {
                YearRecapScreen(onBack = { navController.popBackStack() })
            }
            // Аудиозаписи чужого профиля/сообщества по ссылке. Аргумент строковый:
            // owner_id сообщества отрицательный, а NavType.LongType на «-2000123»
            // в пути не сматчился бы.
            composable(
                NavRoutes.OWNER_AUDIO_ROUTE,
                arguments = listOf(navArgument(NavRoutes.ARG_ID) { type = NavType.StringType })
            ) { entry ->
                val ownerId = entry.arguments?.getString(NavRoutes.ARG_ID)?.toLongOrNull() ?: 0L
                OwnerAudioRoute(
                    ownerId = ownerId,
                    onBack = { navController.popBackStack() }
                )
            }
            // Экран сообщества. Аргумент строковый по той же причине, что у
            // OWNER_AUDIO: в пути лежит отрицательный owner_id.
            composable(
                NavRoutes.GROUP_ROUTE,
                arguments = listOf(navArgument(NavRoutes.ARG_ID) { type = NavType.StringType })
            ) { entry ->
                val ownerId = entry.arguments?.getString(NavRoutes.ARG_ID)?.toLongOrNull() ?: 0L
                com.lmg.vk.ui.screens.GroupScreen(
                    ownerId = ownerId,
                    onBack = { navController.popBackStack() },
                    // Плейлист сообщества открывается штатным экраном плейлиста
                    // внутри ЭТОЙ же вкладки — иначе он лёг бы в чужой бэкстек.
                    onOpenPlaylist = {
                        navController.navigate(NavRoutes.playlist(NavRoutes.TAB_LIBRARY, it))
                    },
                    onOpenMemberProfile = { navController.navigate(NavRoutes.userProfile(it)) }
                )
            }
            composable(
                NavRoutes.USER_PROFILE_ROUTE,
                arguments = listOf(navArgument(NavRoutes.ARG_ID) { type = NavType.StringType })
            ) { entry ->
                val userId = entry.arguments?.getString(NavRoutes.ARG_ID)?.toLongOrNull() ?: 0L
                com.lmg.vk.ui.screens.UserProfileScreen(
                    userId = userId,
                    onBack = { navController.popBackStack() },
                    onOpenMusic = { navController.navigate(NavRoutes.ownerAudio(it)) },
                    onOpenConnections = { kind ->
                        navController.navigate(NavRoutes.userConnections(userId, kind))
                    },
                    onOpenPlaylist = {
                        navController.navigate(NavRoutes.playlist(NavRoutes.TAB_LIBRARY, it))
                    },
                    onOpenDetails = {
                        navController.navigate(NavRoutes.userProfileDetails(userId))
                    },
                )
            }
            composable(
                NavRoutes.USER_PROFILE_DETAILS_ROUTE,
                arguments = listOf(navArgument(NavRoutes.ARG_ID) { type = NavType.StringType }),
            ) { entry ->
                val userId = entry.arguments?.getString(NavRoutes.ARG_ID)?.toLongOrNull() ?: 0L
                com.lmg.vk.ui.screens.UserProfileDetailsScreen(
                    userId = userId,
                    onBack = { navController.popBackStack() },
                    onOpenConnections = { kind ->
                        navController.navigate(NavRoutes.userConnections(userId, kind))
                    },
                )
            }
            composable(
                NavRoutes.USER_CONNECTIONS_ROUTE,
                arguments = listOf(
                    navArgument(NavRoutes.ARG_ID) { type = NavType.StringType },
                    navArgument(NavRoutes.ARG_KIND) { type = NavType.StringType },
                ),
            ) { entry ->
                val userId = entry.arguments?.getString(NavRoutes.ARG_ID)?.toLongOrNull() ?: 0L
                val kind = com.lmg.vk.ui.viewmodel.UserConnectionsKind.fromRoute(
                    entry.arguments?.getString(NavRoutes.ARG_KIND).orEmpty(),
                ) ?: com.lmg.vk.ui.viewmodel.UserConnectionsKind.FOLLOWERS
                com.lmg.vk.ui.screens.UserConnectionsScreen(
                    userId = userId,
                    kind = kind,
                    onBack = { navController.popBackStack() },
                    onOpenUser = { navController.navigate(NavRoutes.userProfile(it)) },
                    onOpenGroup = { navController.navigate(NavRoutes.group(it)) },
                )
            }
            composable(
                NavRoutes.LOCAL_ARTIST_ROUTE,
                arguments = listOf(navArgument(NavRoutes.ARG_NAME) { type = NavType.StringType })
            ) { entry ->
                val name = entry.arguments?.getString(NavRoutes.ARG_NAME).orEmpty()
                LocalArtistDetailScreen(
                    artistName = name,
                    onBack = { navController.popBackStack() },
                    onOpenAlbum = { id, n -> navController.navigate(NavRoutes.localAlbum(id, n)) }
                )
            }
            composable(
                NavRoutes.LOCAL_ALBUM_ROUTE,
                arguments = listOf(
                    navArgument(NavRoutes.ARG_ID) { type = NavType.LongType },
                    navArgument(NavRoutes.ARG_NAME) { type = NavType.StringType }
                )
            ) { entry ->
                val id = entry.arguments?.getLong(NavRoutes.ARG_ID) ?: -1L
                val name = entry.arguments?.getString(NavRoutes.ARG_NAME).orEmpty()
                LocalAlbumDetailScreen(
                    albumId = id,
                    albumName = name,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // ═══════════ Граф NEW ═══════════
        navigation(startDestination = NavRoutes.NEW_HOME, route = NavRoutes.GRAPH_NEW) {
            composable(NavRoutes.NEW_HOME) { entry ->
                val homeViewModel = ViewModelProvider(entry)[com.lmg.vk.ui.viewmodel.HomeViewModel::class.java]
                NewScreen(
                    viewModel = homeViewModel,
                    onNavigateToAlbum = { navController.navigate(NavRoutes.album(NavRoutes.TAB_NEW, it)) },
                    onNavigateToPlaylist = { navController.navigate(NavRoutes.playlist(NavRoutes.TAB_NEW, it)) },
                    onNavigateToArtist = { navController.navigate(NavRoutes.artist(NavRoutes.TAB_NEW, it)) },
                    onNavigateToMusicOwner = {
                        navController.navigate(NavRoutes.ownerAudio(NavRoutes.TAB_NEW, it))
                    },
                    onOpenSnippets = { navController.navigate(NavRoutes.NEW_SNIPPETS) }
                )
            }
            // Лента сниппетов — полноэкранный фид, живёт в бэкстеке вкладки New.
            composable(NavRoutes.NEW_SNIPPETS) {
                SnippetsScreen(onBack = { navController.popBackStack() })
            }
            musicDetailDestinations(NavRoutes.TAB_NEW, navController)
        }

        // ═══════════ Граф НАСТРОЕК ═══════════
        navigation(startDestination = NavRoutes.SETTINGS_HOME, route = NavRoutes.GRAPH_SETTINGS) {
            composable(NavRoutes.SETTINGS_HOME) {
                SettingsScreen(
                    onBack = {},
                    // Экран аудиоэффектов удалён (нет нативных JUCE/Oboe-библиотек);
                    // параметр ещё в сигнатуре SettingsScreen — отдаём пустой колбэк.
                    onOpenEqualizer = {},
                    onOpenProfile = onOpenProfile,
                    onOpenAccounts = onOpenAccounts,
                    onOpenRecommendationsOnboarding = {
                        navController.navigate(NavRoutes.RECOMMENDATIONS_ONBOARDING)
                    },
                    onOpenDebugLog = { navController.navigate(NavRoutes.DEBUG_LOG) },
                    showBack = false,
                    backdrop = backdrop
                )
            }
            // Онбординг рекомендаций ВКонтакте: живёт в графе Настроек, потому
            // что вход на него — пункт «Настроить рекомендации».
            composable(NavRoutes.RECOMMENDATIONS_ONBOARDING) {
                RecommendationsOnboardingScreen(onBack = { navController.popBackStack() })
            }
            // Отладочный лог: живёт в графе Настроек, вход — пункт «Отладочный лог».
            composable(NavRoutes.DEBUG_LOG) {
                com.lmg.vk.ui.screens.DebugLogScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * Общие экраны-детали (альбом/артист/плейлист) для вкладки [tab]. Роуты
 * префиксуются тегом вкладки, поэтому одинаковые экраны не сталкиваются между
 * графами и складываются в бэкстек своей вкладки.
 */
private fun NavGraphBuilder.musicDetailDestinations(
    tab: String,
    navController: NavHostController
) {
    if (tab != NavRoutes.TAB_LIBRARY) {
        composable(
            NavRoutes.ownerAudioRoute(tab),
            arguments = listOf(navArgument(NavRoutes.ARG_ID) { type = NavType.StringType }),
        ) { entry ->
            val ownerId = entry.arguments?.getString(NavRoutes.ARG_ID)?.toLongOrNull() ?: 0L
            OwnerAudioRoute(
                ownerId = ownerId,
                onBack = { navController.popBackStack() },
            )
        }
    }
    composable(
        NavRoutes.albumRoute(tab),
        arguments = listOf(navArgument(NavRoutes.ARG_ID) { type = NavType.StringType })
    ) { entry ->
        val id = entry.arguments?.getString(NavRoutes.ARG_ID).orEmpty()
        AlbumDetailScreen(
            albumId = id,
            onBack = { navController.popBackStack() },
            onNavigateToArtist = { navController.navigate(NavRoutes.artist(tab, it)) },
        )
    }
    composable(
        NavRoutes.artistRoute(tab),
        arguments = listOf(navArgument(NavRoutes.ARG_ID) { type = NavType.StringType })
    ) { entry ->
        val id = entry.arguments?.getString(NavRoutes.ARG_ID).orEmpty()
        ArtistDetailScreen(
            artistId = id,
            onBack = { navController.popBackStack() },
            onNavigateToAlbum = { navController.navigate(NavRoutes.album(tab, it)) },
            onNavigateToArtist = { navController.navigate(NavRoutes.artist(tab, it)) },
            onNavigateToPlaylist = { navController.navigate(NavRoutes.playlist(tab, it)) },
            // Сообщества артиста (своё и похожие) ведут на экран сообщества.
            // Колбэк отдаёт ОТРИЦАТЕЛЬНЫЙ owner_id — конвенция VkGroup.audioOwnerId,
            // её же ждут NavRoutes.group и GroupViewModel.
            onOpenGroup = { navController.navigate(NavRoutes.group(it)) },
        )
    }
    composable(
        NavRoutes.playlistRoute(tab),
        arguments = listOf(navArgument(NavRoutes.ARG_ID) { type = NavType.StringType })
    ) { entry ->
        val id = entry.arguments?.getString(NavRoutes.ARG_ID).orEmpty()
        PlaylistDetailScreen(
            playlistId = id,
            onBack = { navController.popBackStack() },
            onNavigateToArtist = { navController.navigate(NavRoutes.artist(tab, it)) },
        )
    }
}
