package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Samsung One UI Palette
val OneUI_Blue = Color(0xFF3E82F7)
val OneUI_Green = Color(0xFF3ECF8E)
val OneUI_Red = Color(0xFFF74E4E)
val OneUI_Orange = Color(0xFFF7A54E)

val OneUI_Light_Background = Color(0xFFF2F2F7)
val OneUI_Light_Surface = Color(0xFFFFFFFF)
val OneUI_Light_OnSurface = Color(0xFF000000)

val OneUI_Dark_Background = Color(0xFF000000) // AMOLED Black
val OneUI_Dark_Surface = Color(0xFF1C1C1E)
val OneUI_Dark_OnSurface = Color(0xFFFFFFFF)

private val DarkColorScheme = darkColorScheme(
    primary = OneUI_Blue,
    secondary = Color(0xFF8E8E93),
    background = OneUI_Dark_Background,
    surface = OneUI_Dark_Surface,
    onPrimary = Color.White,
    onBackground = OneUI_Dark_OnSurface,
    onSurface = OneUI_Dark_OnSurface,
    surfaceVariant = Color(0xFF2C2C2E)
)

private val LightColorScheme = lightColorScheme(
    primary = OneUI_Blue,
    secondary = Color(0xFF8E8E93),
    background = OneUI_Light_Background,
    surface = OneUI_Light_Surface,
    onPrimary = Color.White,
    onBackground = OneUI_Light_OnSurface,
    onSurface = OneUI_Light_OnSurface,
    surfaceVariant = Color(0xFFE5E5EA)
)

val OneUITypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = (-1).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun DataTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OneUITypography,
        content = content
    )
}
