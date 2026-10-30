package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import com.ivor.openstream.presentation.navigation.DeepLinkRequest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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

private object TvRoutes {
    const val ANIME_BROWSE = "tv_anime/browse?genre={genre}&query={query}"
    fun animeBrowse(genre: String = "", query: String = "") =
        "tv_anime/browse?genre=${android.net.Uri.encode(genre)}&query=${android.net.Uri.encode(query)}"

    const val DETAILS = "tv_details/{mediaType}/{animeId}"
    const val PLAYER = "tv_player/{mediaType}/{animeId}/{season}/{episode}?downloadId={downloadId}"

    fun details(mediaType: String, animeId: Int) = "tv_details/$mediaType/$animeId"
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
        val contentWidth = configuration.screenWidthDp - 84 - TvLayout.ContentStart.value.toInt()
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

    fun go(destination: TvDestination) {
        if (destination == selected && route == destination.route) return
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
            Column(
                modifier = Modifier
                    .width(84.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 10.dp, vertical = 16.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TvDestination.topLevel.forEach { destination ->
                    Button(
                        onClick = { go(destination) },
                        modifier = Modifier
                            .width(62.dp)
                            .height(55.dp)
                            .focusRequester(requesters.getValue(destination)),
                        colors = ButtonDefaults.colors(
                            containerColor = if (destination == selected)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = if (destination == selected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            destination.icon,
                            contentDescription = stringResource(destination.labelRes)
                        )
                    }
                }
            }
        }
        NavHost(
            navController = navController,
            startDestination = TvDestination.HOME.route,
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
            composable(TvDestination.HOME.route) {
                TvHomeScreen(
                    onOpenAnime = { go(TvDestination.ANIME) },
                    onOpenExtensions = { go(TvDestination.EXTENSIONS) },
                    onOpenTitle = { mediaType, id -> navController.navigate(TvRoutes.details(mediaType, id)) },
                    onPlay = { mediaType, id, season, episode ->
                        navController.navigate(TvRoutes.player(mediaType, id, season, episode))
                    }
                )
            }

            composable(TvDestination.ANIME.route) {
                TvAnimeScreen(onOpenSources = { go(TvDestination.EXTENSIONS) })
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
                    onOpenSources = { go(TvDestination.EXTENSIONS) }
                )
            }

            composable(TvDestination.SEARCH.route) {
                TvSearchScreen(
                    onAnimeBrowse = { genre, query ->
                        navController.navigate(TvRoutes.animeBrowse(genre = genre, query = query))
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
                    onOpenExtensions = { go(TvDestination.EXTENSIONS) }
                )
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
                TvDetailsScreen(
                    mediaType = mediaType,
                    animeId = animeId,
                    onPlay = { season, episode ->
                        navController.navigate(TvRoutes.player(mediaType, animeId, season, episode))
                    },
                    onOpenTitle = { type, id -> navController.navigate(TvRoutes.details(type, id)) }
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
                    onOpenDetails = { type, id -> navController.navigate(TvRoutes.details(type, id)) }
                )
            }
        }
    }
}
