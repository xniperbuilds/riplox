package com.xniperbuilds.downloader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.xniperbuilds.downloader.Prefs

private val DarkColorScheme = darkColorScheme(
    primary = RiploxSteel,
    secondary = RiploxSteelDim,
    tertiary = RiploxSteel
)

private val LightColorScheme = lightColorScheme(
    primary = RiploxSteelDeep,
    secondary = RiploxSteelDim,
    tertiary = RiploxSteelDeep

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun XniperDownloaderTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val mode = Prefs.themeMode(context)          // system | light | dark | amoled
    val dynamicColor = Prefs.dynamicColor(context)
    val darkTheme = when (mode) {
        "light" -> false
        "dark", "amoled" -> true
        else -> isSystemInDarkTheme()
    }

    var colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // AMOLED — pure black background (battery + deep black)
    if (mode == "amoled") {
        colorScheme = colorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF0B0B0B)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}