package com.ivor.openstream.presentation.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/** A capability category, not merely the current window width (a TV can be 720p). */
enum class DeviceKind { PHONE, TABLET, TV }

/** True on Android TV / Google TV devices driven by a remote rather than touch. */
object TvDetect {
    fun isTv(context: Context): Boolean {
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true
        if (pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) return true
        val uiMode = (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)
            ?.currentModeType ?: Configuration.UI_MODE_TYPE_UNDEFINED
        return uiMode == Configuration.UI_MODE_TYPE_TELEVISION
    }

    /** TV identification takes priority; tablets are defined by Android's smallest width. */
    fun deviceKind(
        context: Context,
        configuration: Configuration = context.resources.configuration
    ): DeviceKind = when {
        isTv(context) -> DeviceKind.TV
        configuration.smallestScreenWidthDp >= 600 -> DeviceKind.TABLET
        else -> DeviceKind.PHONE
    }

    /** Honor the system animator scale: no focus zoom when the user disabled animation. */
    fun animationsEnabled(context: Context): Boolean = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) != 0f
    }.getOrDefault(true)
}

/** Re-evaluates on configuration changes (foldables, freeform windows, external displays). */
@Composable
fun rememberDeviceKind(): DeviceKind {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration.uiMode, configuration.smallestScreenWidthDp) {
        TvDetect.deviceKind(context, configuration)
    }
}

@Composable
fun rememberTvMode(): Boolean = rememberDeviceKind() == DeviceKind.TV
