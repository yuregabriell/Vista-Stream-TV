package com.ivor.openstream.presentation.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import com.ivor.openstream.presentation.navigation.DeepLinkRequest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.ivor.openstream.presentation.player.PlayerScreen
import com.ivor.openstream.presentation.update.UpdateScreen
import com.ivor.openstream.presentation.details.DetailsViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState

private object TvRoutes {
    const val CATALOG = "tv_catalog"
    const val CATALOG_PLATFORM = "tv_catalog/platform/{providerId}"
    fun catalogPlatform(providerId: Int) = "tv_catalog/platform/$providerId"
    const val ANIME_BROWSE = "tv_anime/browse?genre={genre}&query={query}"
    fun animeBrowse(genre: String = "", query: String = "") =
        "tv_anime/browse?genre=${android.net.Uri.encode(genre)}&query=${android.net.Uri.encode(query)}"

    const val DETAILS = "tv_details/{mediaType}/{animeId}"
    const val EPISODE = "tv_episode/{mediaType}/{animeId}/{season}/{episode}"
    const val PLAYER = "tv_player/{mediaType}/{animeId}/{season}/{episode}?downloadId={downloadId}"
    const val LIVE_PLAYER = "tv_live_player"
    const val UPDATE = "tv_update"

    fun details(mediaType: String, animeId: Int) = "tv_details/$mediaType/$animeId"
    fun episode(mediaType: String, animeId: Int, season: Int, episode: Int) =
        "tv_episode/$mediaType/$animeId/$season/$episode"
    /** With [downloadId] the player opens the copy on the device instead of searching sources. */
    fun player(mediaType: String, animeId: Int, season: Int, episode: Int, downloadId: String? = null) =
        "tv_player/$mediaType/$animeId/$season/$episode" +
            (downloadId?.let { "?downloadId=${android.net.Uri.encode(it)}" } ?: "")
}

private fun parentDestination(route: String?): TvDestination =
    if (route?.startsWith("tv_anime/browse") == true) TvDestination.ANIME
    else TvDestination.topLevel.firstOrNull { it.route == route } ?: TvDestination.HOME

@Composable
fun TvNavigation(
    deepLinkRequest: DeepLinkRequest? = null,
    onDeepLinkHandled: () -> Unit = {}
) {
    TvTheme {
        val navController = rememberNavController()
        val configuration = LocalConfiguration.current
        // Screens still sized by TvDimens get the width right of the collapsed drawer.
        val contentWidth = configuration.screenWidthDp - TvSidebarWidth.value.toInt() - TvLayout.ContentStart.value.toInt()
        val metrics = remember(contentWidth, configuration.screenHeightDp) {
            TvDimens.of(contentWidth, configuration.screenHeightDp)
        }
        CompositionLocalProvider(LocalTvDimens provides metrics) {
            TvAppScaffold(navController, deepLinkRequest, onDeepLinkHandled)
        }
    }
}

/**
 * The TV shell: a navigation drawer that sits collapsed to icons over the content and opens with
 * labels when it takes focus (Left from a screen's first column). Back is left to the back stack:
 * Details and the player go back, a tab goes back to Home, and Home leaves the app. Each screen
 * puts focus on its own first control; the shell never pulls focus into the drawer.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TvAppScaffold(
    navController: NavHostController,
    deepLinkRequest: DeepLinkRequest?,
    onDeepLinkHandled: () -> Unit
) {
    LaunchedEffect(deepLinkRequest?.nonce) {
        val request = deepLinkRequest ?: return@LaunchedEffect
        if (request.tmdbId > 0 && (request.mediaType == "movie" || request.mediaType == "tv")) {
            navController.navigate(TvRoutes.details(request.mediaType, request.tmdbId)) {
                launchSingleTop = true
            }
        }
        onDeepLinkHandled()
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val selected = parentDestination(route)
    // Player and Details are full screen: the drawer is for moving between tabs.
    val showDrawer = TvDestination.topLevel.any { it.route == route } ||
        route?.startsWith("tv_anime/browse") == true
    val requesters = remember { TvDestination.topLevel.associateWith { FocusRequester() } }
    var liveSelection by remember { androidx.compose.runtime.mutableStateOf<TvLiveSelection?>(null) }
    var resetTopToken by remember { mutableIntStateOf(0) }
    var sidebarHasFocus by remember { androidx.compose.runtime.mutableStateOf(false) }
    // Back on a top-level screen deliberately opens the sidebar. Back inside
    // episode details/player still belongs to the NavHost; a second Back from
    // an already focused sidebar retains normal Android navigation.
    BackHandler(enabled = showDrawer && !sidebarHasFocus &&
        TvDestination.topLevel.any { it.route == route }) {
        runCatching { requesters.getValue(selected).requestFocus() }
    }

    fun go(destination: TvDestination) {
        if (destination == selected && route == destination.route) {
            // Re-pressing the CURRENT sidebar tab returns that screen to its
            // top. Home/Anime animate there without refetching the catalog.
            if (destination == TvDestination.HOME || destination == TvDestination.ANIME) {
                resetTopToken++
            } else {
                navController.navigate(destination.route) {
                    popUpTo(destination.route) { inclusive = true }
                    launchSingleTop = true
                    restoreState = false
                }
            }
            return
        }
        navController.navigate(destination.route) {
            popUpTo(TvDestination.HOME.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // A modal TV drawer intercepts DPAD focus while content is open. Keep the rail
    // in the normal layout instead: pressing Right from a menu item moves into
    // the screen, and the menu can never float over or steal its cards/episodes.
    Row(modifier = Modifier.fillMaxSize()) {
        if (showDrawer) {
            // Selection is drawn inside each icon's own surface so it shares
            // exactly the same bounds as the icon (including different TV densities).
            Box(
                modifier = Modifier
                    .width(TvSidebarWidth)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.46f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.10f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 6.dp, vertical = 16.dp)
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TvDestination.topLevel.forEach { destination ->
                        TvSidebarNavItem(
                            destination = destination,
                            selected = destination == selected,
                            onClick = { go(destination) },
                            modifier = Modifier
                                .focusRequester(requesters.getValue(destination))
                                .onFocusChanged { sidebarHasFocus = it.hasFocus }
                        )
                    }
                }
            }
        }
        NavHost(
            navController = navController,
            startDestination = TvDestination.HOME.route,
            modifier = Modifier.weight(1f).fillMaxHeight()
                // Directional UP/DOWN from the content must never exit to the
                // sidebar when a LazyColumn repositions cards. Only LEFT may
                // cross the content boundary into the navigation rail.
                .focusProperties {
                    exit = { direction ->
                        when (direction) {
                            FocusDirection.Up, FocusDirection.Down, FocusDirection.Right ->
                                FocusRequester.Cancel
                            else -> FocusRequester.Default
                        }
                    }
                }
                .focusGroup()
        ) {
            composable(TvDestination.HOME.route) {
                TvHomeScreen(
                    resetTopToken = resetTopToken,
                    onOpenAnime = { go(TvDestination.ANIME) },
                    onBrowseCatalog = { navController.navigate(TvRoutes.CATALOG) },
                    onOpenExtensions = { go(TvDestination.EXTENSIONS) },
                    onOpenTitle = { mediaType, id -> navController.navigate(TvRoutes.details(mediaType, id)) },
                    onPlay = { mediaType, id, season, episode ->
                        // Pressing the clearly labelled Play button always starts playback.
                        navController.navigate(TvRoutes.player(mediaType, id, season, episode))
                    },
                    onOpenEpisode = { mediaType, id, season, episode ->
                        // Episode cards open the Netflix-style landing page, where sources
                        // are discovered before playback begins.
                        navController.navigate(TvRoutes.episode(mediaType, id, season, episode)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(TvRoutes.CATALOG) {
                TvCatalogScreen(
                    onOpenTitle = { mediaType, id ->
                        navController.navigate(TvRoutes.details(mediaType, id))
                    }
                )
            }

            composable(
                TvRoutes.CATALOG_PLATFORM,
                arguments = listOf(navArgument("providerId") { type = NavType.IntType })
            ) { entry ->
                TvCatalogScreen(
                    initialProviderId = entry.arguments?.getInt("providerId"),
                    onOpenTitle = { mediaType, id ->
                        navController.navigate(TvRoutes.details(mediaType, id))
                    }
                )
            }

            composable(TvDestination.ANIME.route) {
                TvAnimeScreen(
                    resetTopToken = resetTopToken,
                    onOpenSources = { go(TvDestination.EXTENSIONS) },
                    onOpenMovies = { go(TvDestination.HOME) },
                    onOpenUnifiedTitle = { mediaType, id ->
                        navController.navigate(TvRoutes.details(mediaType, id))
                    }
                )
            }

            composable(
                TvRoutes.ANIME_BROWSE,
                arguments = listOf(
                    navArgument("genre") { type = NavType.StringType; defaultValue = "" },
                    navArgument("query") { type = NavType.StringType; defaultValue = "" }
                )
            ) { entry ->
                TvAnimeScreen(
                    initialGenre = entry.arguments?.getString("genre").orEmpty(),
                    initialQuery = entry.arguments?.getString("query").orEmpty(),
                    initialBrowse = true,
                    onOpenSources = { go(TvDestination.EXTENSIONS) },
                    onOpenUnifiedTitle = { mediaType, id ->
                        navController.navigate(TvRoutes.details(mediaType, id))
                    }
                )
            }

            composable(TvDestination.LIVE.route) {
                TvLiveScreen(onPlayChannel = { channel, channels, country ->
                    liveSelection = TvLiveSelection(country, channels, channel.id)
                    navController.navigate(TvRoutes.LIVE_PLAYER) { launchSingleTop = true }
                })
            }

            // Separate immersive destination: no sidebar, Dialog, or inset-constrained
            // preview. Same ExoPlayerView as the movie/series player.
            composable(TvRoutes.LIVE_PLAYER) {
                val selection = liveSelection
                if (selection != null) {
                    TvLivePlayerScreen(
                        selection = selection,
                        onExit = { navController.popBackStack() }
                    )
                }
            }

            composable(TvDestination.SEARCH.route) {
                TvSearchScreen(
                    onAnimeBrowse = { genre, query ->
                        navController.navigate(TvRoutes.animeBrowse(genre = genre, query = query))
                    },
                    onOpenPlatform = { providerId ->
                        navController.navigate(TvRoutes.catalogPlatform(providerId))
                    },
                    onOpenSources = { go(TvDestination.EXTENSIONS) },
                    onTitleClick = { animeId, mediaType -> navController.navigate(TvRoutes.details(mediaType, animeId)) }
                )
            }

            composable(TvDestination.SAVED.route) {
                TvSavedScreen(
                    onOpenTitle = { animeId, mediaType -> navController.navigate(TvRoutes.details(mediaType, animeId)) }
                )
            }

            composable(TvDestination.DOWNLOADS.route) {
                TvDownloadsScreen(
                    onPlay = { download ->
                        navController.navigate(
                            TvRoutes.player(
                                download.mediaType,
                                download.tmdbId,
                                download.season,
                                download.episode,
                                downloadId = download.downloadId
                            )
                        )
                    }
                )
            }

            composable(TvDestination.EXTENSIONS.route) {
                TvExtensionsScreen()
            }

            composable(TvDestination.SETTINGS.route) {
                TvSettingsScreen(
                    onOpenExtensions = { go(TvDestination.EXTENSIONS) },
                    onOpenUpdate = { navController.navigate(TvRoutes.UPDATE) { launchSingleTop = true } }
                )
            }

            composable(TvRoutes.UPDATE) {
                UpdateScreen(onBackClick = { navController.popBackStack() })
            }

            composable(
                route = TvRoutes.DETAILS,
                arguments = listOf(
                    navArgument("mediaType") { type = NavType.StringType },
                    navArgument("animeId") { type = NavType.IntType }
                )
            ) { entry ->
                val mediaType = entry.arguments?.getString("mediaType") ?: "tv"
                val animeId = entry.arguments?.getInt("animeId") ?: return@composable
                // All TV entry points (Home rails, catalog, search, saved, deep links)
                // share the same premium detail UI as Continue Watching.
                val titleVm: DetailsViewModel = hiltViewModel()
                val resume by titleVm.resumeTarget.collectAsState()
                val targetSeason = if (mediaType == "movie") 1 else resume?.season?.takeIf { it > 0 } ?: 1
                val targetEpisode = if (mediaType == "movie") 1 else resume?.episode?.takeIf { it > 0 } ?: 1
                TvEpisodeDetailsScreen(
                    mediaType = mediaType,
                    animeId = animeId,
                    season = targetSeason,
                    episode = targetEpisode,
                    onPlay = { navController.navigate(TvRoutes.player(mediaType, animeId, targetSeason, targetEpisode)) },
                    onPlayEpisode = { selectedSeason, selectedEpisode ->
                        navController.navigate(TvRoutes.player(mediaType, animeId, selectedSeason, selectedEpisode))
                    },
                    onChooseEpisode = { selectedSeason, selectedEpisode ->
                        navController.navigate(TvRoutes.episode(mediaType, animeId, selectedSeason, selectedEpisode))
                    }
                )
            }

            composable(
                route = TvRoutes.EPISODE,
                arguments = listOf(
                    navArgument("mediaType") { type = NavType.StringType },
                    navArgument("animeId") { type = NavType.IntType },
                    navArgument("season") { type = NavType.IntType },
                    navArgument("episode") { type = NavType.IntType }
                )
            ) { entry ->
                val args = entry.arguments ?: return@composable
                val mediaType = args.getString("mediaType") ?: "tv"
                val animeId = args.getInt("animeId")
                val season = args.getInt("season")
                val episode = args.getInt("episode")
                TvEpisodeDetailsScreen(
                    mediaType = mediaType,
                    animeId = animeId,
                    season = season,
                    episode = episode,
                    onPlay = { navController.navigate(TvRoutes.player(mediaType, animeId, season, episode)) },
                    onPlayEpisode = { selectedSeason, selectedEpisode ->
                        // Episode cards are direct play actions; leaving the previous episode
                        // landing behind would show stale metadata after pressing Back.
                        navController.navigate(TvRoutes.player(mediaType, animeId, selectedSeason, selectedEpisode)) {
                            popUpTo(TvRoutes.EPISODE) { inclusive = true }
                        }
                    },
                    onChooseEpisode = { nextSeason, nextEpisode ->
                        navController.navigate(TvRoutes.episode(mediaType, animeId, nextSeason, nextEpisode)) {
                            popUpTo(TvRoutes.EPISODE) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = TvRoutes.PLAYER,
                arguments = listOf(
                    navArgument("mediaType") { type = NavType.StringType },
                    navArgument("animeId") { type = NavType.IntType },
                    navArgument("season") { type = NavType.IntType },
                    navArgument("episode") { type = NavType.IntType },
                    navArgument("downloadId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                val args = entry.arguments ?: return@composable
                val mediaType = args.getString("mediaType") ?: "tv"
                val animeId = args.getInt("animeId")
                PlayerScreen(
                    mediaType = mediaType,
                    tmdbId = animeId,
                    season = args.getInt("season"),
                    episode = args.getInt("episode"),
                    downloadId = args.getString("downloadId"),
                    tvControls = true,
                    onBackClick = { navController.popBackStack() },
                    onEpisodeClick = { newSeason, newEpisode ->
                        navController.navigate(TvRoutes.player(mediaType, animeId, newSeason, newEpisode)) {
                            popUpTo(TvRoutes.PLAYER) { inclusive = true }
                        }
                    },
                    onEpisodeDetailsClick = { nextSeason, nextEpisode ->
                        navController.navigate(TvRoutes.episode(mediaType, animeId, nextSeason, nextEpisode))
                    },
                    onOpenDetails = { type, id -> navController.navigate(TvRoutes.details(type, id)) }
                )
            }
        }
    }
}
