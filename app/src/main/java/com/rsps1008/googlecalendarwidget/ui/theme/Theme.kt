package com.rsps1008.googlecalendarwidget.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7BD5F5),
    onPrimary = Color(0xFF0A2731),
    secondary = Color(0xFF80CBC4),
    tertiary = Color(0xFFFF8A70),
    background = Color(0xFF101719),
    surface = Color(0xFF1E3037),
    onSurface = Color(0xFFEAF4F6),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF168DBB),
    onPrimary = Color.White,
    secondary = Color(0xFF347F79),
    tertiary = Color(0xFFD75B40),
    background = Color(0xFFF3F6F6),
    surface = Color.White,
    onSurface = Color(0xFF20343C),
)

@Composable
fun GoogleCalendarWidgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
