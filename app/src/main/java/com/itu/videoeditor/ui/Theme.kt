package com.itu.videoeditor.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itu.videoeditor.ui.components.PrismMotion

val Obsidian = Color(0xFF080C12)
val Graphite = Color(0xFF121923)
val Raised = Color(0xFF192330)
val Edge = Color(0xFF2B3949)
val Cyan = Color(0xFF8CE7F2)
val Violet = Color(0xFFBAAFF0)
val Muted = Color(0xFFA1AFC1)

@Composable
fun ObsidianPrism(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, secondary = Violet, background = Obsidian,
        surface = Graphite, surfaceVariant = Raised, onPrimary = Obsidian, outline = Edge,
        onSurface = Color(0xFFEAF1F8), onBackground = Color(0xFFEAF1F8)),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.8).sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 25.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp),
            titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 23.sp)),
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp))) {
        PrismMotion(content)
    }
}
