package com.khaiphraban.marketplace.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColors = lightColorScheme(
    primary = Color(0xFF7B4F21),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0B2),
    secondary = Color(0xFF9A6A2F),
    background = Color(0xFFFFFBF6),
    surface = Color(0xFFFFFBF6),
    surfaceVariant = Color(0xFFF4EBDD),
    error = Color(0xFFB3261E)
)

@Composable
fun KhaiPhraBanTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, content = content)
}
