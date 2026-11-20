package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DostDarkColorScheme = darkColorScheme(
    primary = CyanBioluminescent,
    onPrimary = CosmicMidnight,
    primaryContainer = CyanContainerDark,
    onPrimaryContainer = CyanOnContainerDark,
    secondary = AmberWarm,
    onSecondary = CosmicMidnight,
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = AmberOnContainerDark,
    tertiary = VioletAccent,
    onTertiary = CosmicMidnight,
    background = CosmicMidnight,
    onBackground = TextPrimaryDark,
    surface = CosmicNavy,
    onSurface = TextPrimaryDark,
    surfaceVariant = CosmicSurface,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = CosmicMidnight,
    surfaceContainerLow = CosmicNavy,
    surfaceContainer = CosmicSurface,
    surfaceContainerHigh = CosmicSurfaceHigh,
    surfaceContainerHighest = Color(0xFF233659),
    outline = CosmicOutline,
    outlineVariant = Color(0xFF1E2D4A),
    error = RoseErrorDark,
    onError = CosmicMidnight,
    errorContainer = RoseErrorContainerDark,
    onErrorContainer = Color(0xFFFFD9E2)
)

private val DostLightColorScheme = lightColorScheme(
    primary = OceanPrimary,
    onPrimary = Color.White,
    primaryContainer = OceanContainerLight,
    onPrimaryContainer = OceanOnContainerLight,
    secondary = AmberLight,
    onSecondary = Color.White,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = AmberOnContainerLight,
    tertiary = Color(0xFF6D28D9),
    onTertiary = Color.White,
    background = IvoryBackground,
    onBackground = TextPrimaryLight,
    surface = IvorySurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = IvorySurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = IvoryBackground,
    surfaceContainer = IvorySurfaceVariant,
    surfaceContainerHigh = IvorySurfaceHigh,
    surfaceContainerHighest = Color(0xFFD5E0F2),
    outline = IvoryOutline,
    outlineVariant = Color(0xFFDCE5F2),
    error = RoseErrorLight,
    onError = Color.White,
    errorContainer = RoseErrorContainerLight,
    onErrorContainer = Color(0xFF881337)
)

val DostShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

enum class ThemePreference {
    DARK, LIGHT, SYSTEM
}

@Composable
fun DostAiTheme(
    themePreference: ThemePreference = ThemePreference.DARK,
    content: @Composable () -> Unit
) {
    val isDark = when (themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (isDark) DostDarkColorScheme else DostLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DostTypography,
        shapes = DostShapes,
        content = content
    )
}
