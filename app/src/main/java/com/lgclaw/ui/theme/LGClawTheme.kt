package com.lgclaw.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0F6B57),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBCEFE0),
    onPrimaryContainer = Color(0xFF06241D),
    secondary = Color(0xFF4E5F8A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE3FF),
    onSecondaryContainer = Color(0xFF111B35),
    tertiary = Color(0xFF9A5A00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDA8),
    onTertiaryContainer = Color(0xFF321900),
    background = Color(0xFFF6F8F4),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFE0E7E1),
    onSurfaceVariant = Color(0xFF404A45),
    outline = Color(0xFF707B74),
    outlineVariant = Color(0xFFC0CAC3),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF86D9C4),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005141),
    onPrimaryContainer = Color(0xFFA2F6DF),
    secondary = Color(0xFFBBC6F5),
    onSecondary = Color(0xFF202B4B),
    secondaryContainer = Color(0xFF374263),
    onSecondaryContainer = Color(0xFFDCE3FF),
    tertiary = Color(0xFFFFBA4B),
    onTertiary = Color(0xFF512D00),
    tertiaryContainer = Color(0xFF744300),
    onTertiaryContainer = Color(0xFFFFDDA8),
    background = Color(0xFF101512),
    onBackground = Color(0xFFE0E5DF),
    surface = Color(0xFF171D1A),
    onSurface = Color(0xFFE0E5DF),
    surfaceVariant = Color(0xFF404A45),
    onSurfaceVariant = Color(0xFFC0CAC3),
    outline = Color(0xFF8A958E),
    outlineVariant = Color(0xFF404A45),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun LGClawTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity
            val window = activity?.window
            if (window != null) {
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalChatVisuals provides if (darkTheme) com.lgclaw.ui.theme.DarkVisuals else com.lgclaw.ui.theme.LightVisuals
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LGClawTypography,
            content = content
        )
    }
}
