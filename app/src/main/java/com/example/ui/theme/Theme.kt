package com.example.ui.theme

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
import com.example.AppAccent

@Composable
fun MyApplicationTheme(
    accent: AppAccent = AppAccent.BLUE,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) {
        darkColorScheme(
            primary = accent.primary,
            primaryContainer = accent.primary.copy(alpha = 0.15f),
            secondary = accent.primary,
            secondaryContainer = accent.primary.copy(alpha = 0.1f),
            tertiary = accent.text,
            background = Color(0xFF090D16), // Deep Cyber Obsidian Navy
            surface = Color(0xFF131B2E),    // Slate Glass Navy Card
            surfaceVariant = Color(0xFF1E293B),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = Color(0xFFF8FAFC),
            onSurface = Color(0xFFF1F5F9),
            error = ErrorRed,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = accent.primary,
            primaryContainer = accent.container,
            secondary = accent.primary,
            secondaryContainer = accent.container,
            tertiary = accent.text,
            background = accent.container.copy(alpha = 0.35f), // Soft pastel background matching theme accent
            surface = Color.White,          // Clean white surface for cards
            surfaceVariant = Color(0xFFF8FAFC),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = Color(0xFF0F172A),
            onSurface = Color(0xFF0F172A),
            error = ErrorRed,
            onError = Color.White
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
