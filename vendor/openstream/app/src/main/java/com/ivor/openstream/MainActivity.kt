package com.ivor.openstream

import android.content.Context
import android.net.Uri
import com.ivor.openstream.data.auth.VistaCloudAuth
import com.ivor.openstream.presentation.auth.VistaSignInGate
import com.ivor.openstream.presentation.settings.VistaIdAccountActivity
import com.ivor.openstream.presentation.settings.SettingsScreen
import com.ivor.openstream.presentation.settings.VideoShaderSettingsScreen
import com.ivor.openstream.presentation.update.UpdateScreen
import com.ivor.openstream.presentation.profiles.ProfilesViewModel
import com.ivor.openstream.presentation.profiles.WhoIsWatchingScreen
import com.ivor.openstream.presentation.profiles.ManageProfilesScreen
import com.ivor.openstream.presentation.marketplace.MarketplaceScreen
import com.ivor.openstream.presentation.tv.TvSettingsScreen
import com.ivor.openstream.presentation.tv.TvExtensionsScreen
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import com.ivor.openstream.presentation.update.VistaAutoUpdatePrompt
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ivor.openstream.presentation.navigation.AppNavigation
import com.ivor.openstream.presentation.navigation.DeepLinkRequest
import com.ivor.openstream.presentation.navigation.DeepLinks
import com.ivor.openstream.presentation.tv.TvNavigation
import com.ivor.openstream.presentation.tv.TvLiveRemoteKeys
import com.ivor.openstream.presentation.tv.DeviceKind
import com.ivor.openstream.presentation.tv.TvDetect
import com.ivor.openstream.presentation.tv.rememberDeviceKind
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.data.settings.AppLocale
import com.ivor.openstream.data.settings.ThemeMode
import com.ivor.openstream.presentation.player.session.PlaybackSession
import com.ivor.openstream.presentation.shortcuts.AppShortcut
import com.ivor.openstream.presentation.shortcuts.ShortcutRequest
import com.ivor.openstream.ui.theme.OpenStreamTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appSettings: AppSettingsStore

    @Inject
    lateinit var playbackSession: PlaybackSession

    private var shortcutRequest by mutableStateOf<ShortcutRequest?>(null)
    private var deepLinkRequest by mutableStateOf<DeepLinkRequest?>(null)
    private var vistaSignedIn by mutableStateOf(false)
    private var vistaLocalUserId by mutableStateOf<String?>(null)
    private var profileSelectedOnLaunch by mutableStateOf(false)
    private var accessPage by mutableStateOf("profiles")

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase, AppLocale.savedTag(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VistaCloudAuth(this).let { account ->
            vistaSignedIn = account.hasSession()
            vistaLocalUserId = account.localUserId()
        }

        // Mirror the saved theme into AppCompat's night mode so the cold-start window
        // (DayNight theme) matches the Compose theme even with forced light/dark.
        // Runtime switches stay recompose-only; this is re-applied on every creation.
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            when (appSettings.current.themeMode) {
                ThemeMode.LIGHT -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.DARK -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                ThemeMode.SYSTEM -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )

        // AMOLED's page is true black, so the window behind Compose must be black too; otherwise
        // the charcoal DayNight surface shows through during the first frame and in transient
        // gaps such as rotation. The setting only changes dark surfaces, so light themes keep
        // their normal window color.
        val startsDark = when (appSettings.current.themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> {
                val nightMode = resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
        if (appSettings.current.amoledBlack && startsDark) {
            window.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK)
            )
        }

        // First launch only; re-asking on every rotation while the user decides is a bug.
        if (savedInstanceState == null &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                0
            )
        }

        playbackSession.initCast()
        AppShortcut.publish(this)
        // Only a fresh launch; a recreated activity already acted on its shortcut.
        if (savedInstanceState == null) handleLaunchIntent(intent)

        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val settings by appSettings.settings.collectAsState()
            val deviceKind = rememberDeviceKind()
            // The phone/tablet distinction remains available for future responsive features.
            val tvMode = deviceKind == DeviceKind.TV
            val amoled = settings.amoledBlack
            val darkTheme = if (tvMode) true else when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            OpenStreamTheme(
                darkTheme = darkTheme,
                dynamicColor = if (tvMode) false else settings.dynamicColor,
                amoled = amoled
            ) {
                // Most screens draw their own background without a Scaffold, so this root
                // Surface is what gives un-styled Text the theme's onBackground color.
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) {
                        if (!vistaSignedIn) {
                            VistaSignInGate(
                                onLogin = {
                                    startActivity(Intent(this@MainActivity, VistaIdAccountActivity::class.java))
                                },
                                onGetVistaId = {
                                    val page = Uri.parse(
                                        "https://github.com/yuregabriell/VistaID/releases/tag/v0.3.0-supabase-preview"
                                    )
                                    runCatching {
                                        startActivity(Intent(Intent.ACTION_VIEW, page))
                                    }.onFailure {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Abre github.com/yuregabriell/VistaID/releases no navegador",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            )
                        } else if (!profileSelectedOnLaunch) {
                            BackHandler(enabled = accessPage != "profiles") {
                                accessPage = if (accessPage == "settings" || accessPage == "manage")
                                    "profiles" else "settings"
                            }
                            val profilesModel: ProfilesViewModel = hiltViewModel()
                            val profiles by profilesModel.profiles.collectAsState()
                            val active by profilesModel.activeProfile.collectAsState()
                            when (accessPage) {
                                "profiles" -> WhoIsWatchingScreen(
                                    profiles = profiles,
                                    activeId = active?.id,
                                    viewModel = profilesModel,
                                    onSelect = { profile ->
                                        profilesModel.select(profile.id)
                                        profileSelectedOnLaunch = true
                                        accessPage = "profiles"
                                    },
                                    onManage = { accessPage = "manage" },
                                    onSettings = { accessPage = "settings" }
                                )
                                "manage" -> ManageProfilesScreen(
                                    viewModel = profilesModel,
                                    onBackClick = { accessPage = "profiles" }
                                )
                                "settings" -> {
                                    if (tvMode) {
                                        TvSettingsScreen(
                                            onOpenExtensions = { accessPage = "marketplace" },
                                            onOpenUpdate = { accessPage = "updates" }
                                        )
                                    } else {
                                        SettingsScreen(
                                            onBackClick = { accessPage = "profiles" },
                                            onOpenMarketplace = { accessPage = "marketplace" },
                                            onOpenUpdate = { accessPage = "updates" },
                                            onOpenProfiles = { accessPage = "manage" },
                                            onOpenVideoShaders = { accessPage = "shaders" }
                                        )
                                    }
                                }
                                "updates" -> UpdateScreen(
                                    onBackClick = { accessPage = "settings" }
                                )
                                "marketplace" -> {
                                    if (tvMode) {
                                        TvExtensionsScreen()
                                    } else {
                                        MarketplaceScreen(
                                            onBackClick = { accessPage = "settings" },
                                            onSettingsClick = { accessPage = "settings" }
                                        )
                                    }
                                }
                                "shaders" -> VideoShaderSettingsScreen(
                                    onBackClick = { accessPage = "settings" }
                                )
                            }
                        } else if (tvMode) {
                            TvNavigation(
                                deepLinkRequest = deepLinkRequest,
                                onDeepLinkHandled = { deepLinkRequest = null }
                            )
                        } else {
                            AppNavigation(
                                windowSizeClass = windowSizeClass.widthSizeClass,
                                shortcutRequest = shortcutRequest,
                                onShortcutHandled = { shortcutRequest = null },
                                deepLinkRequest = deepLinkRequest,
                                onDeepLinkHandled = { deepLinkRequest = null }
                            )
                        }
                        // Updates are now intentionally opened inside Settings, not
                        // as a global prompt over the login/profile screens.
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val account = VistaCloudAuth(this)
        val updated = account.hasSession()
        val newId = account.localUserId()
        if (updated != vistaSignedIn || newId != vistaLocalUserId) {
            // Different Vista ID accounts must never inherit an already-selected
            // local profile from a prior account/session.
            profileSelectedOnLaunch = false
            accessPage = "profiles"
        }
        vistaSignedIn = updated
        vistaLocalUserId = newId
    }

    override fun onStop() {
        // A TV is a foreground viewing device, not a background audio player.
        // Clear the stream and its media service when Home or another app takes focus.
        // Keep phone/tablet background playback and Cast behaviour untouched.
        if (TvDetect.isTv(this)) {
            playbackSession.stop()
            stopService(Intent(this, com.ivor.openstream.presentation.player.PlaybackService::class.java))
        }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        AppShortcut.from(intent)?.let { shortcutRequest = ShortcutRequest(it) }
        DeepLinks.from(intent)?.let { deepLinkRequest = it }
        if (DeepLinks.isUnrecognisedShare(intent)) {
            Toast.makeText(this, getString(R.string.er_tmdb_links), Toast.LENGTH_LONG).show()
        }
    }

    /** While casting, the volume keys change the TV's volume rather than the phone's. */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // CH+/CH- belong to live TV only while its fullscreen player is active.
        // VOLUME_UP/DOWN remain system/Chromecast volume keys.
        if (TvLiveRemoteKeys.dispatch(event)) return true
        val up = when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_UP -> true
            android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> false
            else -> return super.dispatchKeyEvent(event)
        }
        if (!playbackSession.castStatus.value.isCasting) return super.dispatchKeyEvent(event)
        if (event.action == android.view.KeyEvent.ACTION_DOWN) playbackSession.adjustCastVolume(up)
        return true
    }
}
