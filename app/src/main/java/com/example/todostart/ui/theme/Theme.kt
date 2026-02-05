package com.example.todostart.ui.theme

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

/**
 * WAŻNE:
 * - dynamicColor = false -> wyłącza "dynamic colors" (Android 12+)
 * - ustawiamy własne szarości, żeby nie było białych wkładów w kartach
 */

// Stałe kolory (szare, spójne)
private val Bg = Color(0xFFE6E3E8)          // tło aplikacji (szarawy / lekko fioletowy)
private val Surface = Color(0xFFDDD8E0)     // powierzchnie (kafelki)
private val Surface2 = Color(0xFFD2CCD6)    // surfaceVariant (np. panele filtrów)
private val Outline = Color(0xFF9A93A0)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6E5DAA),
    secondary = Color(0xFF5F5A6B),
    tertiary = Color(0xFF7A6A86),

    background = Bg,
    surface = Surface,
    surfaceVariant = Surface2,

    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,

    onBackground = Color(0xFF1B1B1F),
    onSurface = Color(0xFF1B1B1F),
    onSurfaceVariant = Color(0xFF2A2630),

    outline = Outline
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFBFAEFF),
    secondary = Color(0xFFC9C2D6),
    tertiary = Color(0xFFE1B7D7),

    background = Color(0xFF121014),
    surface = Color(0xFF1B1820),
    surfaceVariant = Color(0xFF24202A),

    onPrimary = Color(0xFF1B1B1F),
    onSecondary = Color(0xFF1B1B1F),
    onTertiary = Color(0xFF1B1B1F),

    onBackground = Color(0xFFEAE6EF),
    onSurface = Color(0xFFEAE6EF),
    onSurfaceVariant = Color(0xFFDAD3E3),

    outline = Color(0xFF6E6776)
)

@Composable
fun TodoStartTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 🔥 TU: dynamicColor wyłączone na stałe, żeby pozbyć się białych wkładów
    dynamicColor: Boolean = false,
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
