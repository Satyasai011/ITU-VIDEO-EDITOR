package com.itu.videoeditor.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val Obsidian = Color(0xFF080B10)
val Graphite = Color(0xFF141A23)
val Cyan = Color(0xFF80EDFF)
val Violet = Color(0xFFB5A2FF)
val Muted = Color(0xFF96A4B8)

@Composable
fun ObsidianPrism(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, secondary = Violet, background = Obsidian,
        surface = Graphite, surfaceVariant = Color(0xFF202A36), onPrimary = Obsidian,
        onSurface = Color(0xFFF0F5FC), onBackground = Color(0xFFF0F5FC)),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)), content = content)
}
