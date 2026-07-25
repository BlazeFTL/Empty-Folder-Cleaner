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
    accent: AppAccent = AppAccent.BREEZE_BLUE,
    darkTheme: Boolean = false, // Enforce light theme by default as per user request
    dynamicColor: Boolean = false, // Set to false to prioritize our handcrafted Clean Breeze theme
    content: @Composable () -> Unit,
) {
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        lightColorScheme(
            primary = accent.primary,
            primaryContainer = accent.container,
            secondary = accent.secondaryColor,
            secondaryContainer = accent.container,
            tertiary = accent.text,
            background = accent.container, // Dynamic theme-tinted background canvas
            surface = Color.White,          // Clean white surface for cards
            surfaceVariant = Color.White,
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
