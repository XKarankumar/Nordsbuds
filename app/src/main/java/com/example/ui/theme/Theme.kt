package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val NordDarkColorScheme = darkColorScheme(
    primary = OnePlusRed,
    onPrimary = TextPrimary,
    primaryContainer = OnePlusRedContainer,
    onPrimaryContainer = OnePlusRedGlow,
    secondary = OnePlusRedGlow,
    onSecondary = AmoledBlack,
    secondaryContainer = ObsidianElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = EmeraldActive,
    onTertiary = AmoledBlack,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = EmeraldActive,
    background = AmoledBlack,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianBorder,
    outlineVariant = CrimsonBorder,
    error = ErrorCrimson,
    onError = AmoledBlack,
)

private val NordShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun NordBudsPremiumTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = NordDarkColorScheme,
        typography = NordTypography,
        shapes = NordShapes,
        content = content,
    )
}
