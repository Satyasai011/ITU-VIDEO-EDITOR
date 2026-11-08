package com.itu.videoeditor.ui.components

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.itu.videoeditor.ui.*

val LocalPrismMotion = staticCompositionLocalOf { true }

@Composable
fun PrismMotion(content: @Composable () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    fun enabled() = runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0 &&
            Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) > 0
    }.getOrDefault(false)
    var motion by remember { mutableStateOf(enabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { motion = enabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    CompositionLocalProvider(LocalPrismMotion provides motion, content = content)
}

enum class Glyph { Prism, Studio, Vault, Settings, Plus, Back, Grid, List, Search, Sort, Play, Pause, Trim, Export, Save, Close, Retry, Info, Start, End }

private val glyphs = Glyph.entries.associateWith { glyph ->
    ImageVector.Builder(glyph.name, 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            when (glyph) {
                Glyph.Prism -> { moveTo(12f,2f); lineTo(21f,9f); lineTo(12f,22f); lineTo(3f,9f); close(); moveTo(3f,9f); lineTo(21f,9f); moveTo(12f,2f); lineTo(8f,9f); lineTo(12f,22f); lineTo(16f,9f); close() }
                Glyph.Studio -> { moveTo(4f,10f); lineTo(12f,3f); lineTo(20f,10f); lineTo(20f,21f); lineTo(4f,21f); close(); moveTo(9f,21f); lineTo(9f,14f); lineTo(15f,14f); lineTo(15f,21f) }
                Glyph.Vault -> { moveTo(3f,7f); lineTo(9f,7f); lineTo(11f,4f); lineTo(21f,4f); lineTo(21f,20f); lineTo(3f,20f); close(); moveTo(3f,10f); lineTo(21f,10f) }
                Glyph.Settings -> { moveTo(4f,6f); lineTo(20f,6f); moveTo(4f,12f); lineTo(20f,12f); moveTo(4f,18f); lineTo(20f,18f); moveTo(8f,3f); lineTo(8f,9f); moveTo(16f,9f); lineTo(16f,15f); moveTo(10f,15f); lineTo(10f,21f) }
                Glyph.Plus -> { moveTo(12f,5f); lineTo(12f,19f); moveTo(5f,12f); lineTo(19f,12f) }
                Glyph.Back -> { moveTo(14f,5f); lineTo(7f,12f); lineTo(14f,19f); moveTo(7f,12f); lineTo(21f,12f) }
                Glyph.Grid -> { for(x in listOf(4f,14f)) for(y in listOf(4f,14f)) { moveTo(x,y); lineTo(x+6,y); lineTo(x+6,y+6); lineTo(x,y+6); close() } }
                Glyph.List -> { for(y in listOf(6f,12f,18f)) { moveTo(4f,y); lineTo(5f,y); moveTo(9f,y); lineTo(20f,y) } }
                Glyph.Search -> { moveTo(16f,10f); curveTo(16f,18f,4f,18f,4f,10f); curveTo(4f,2f,16f,2f,16f,10f); close(); moveTo(15f,15f); lineTo(21f,21f) }
                Glyph.Sort -> { moveTo(7f,4f); lineTo(7f,20f); moveTo(3f,16f); lineTo(7f,20f); lineTo(11f,16f); moveTo(13f,5f); lineTo(21f,5f); moveTo(13f,11f); lineTo(19f,11f); moveTo(13f,17f); lineTo(17f,17f) }
                Glyph.Play -> { moveTo(8f,4f); lineTo(21f,12f); lineTo(8f,20f); close() }
                Glyph.Pause -> { moveTo(7f,4f); lineTo(7f,20f); moveTo(17f,4f); lineTo(17f,20f) }
                Glyph.Trim -> { moveTo(7f,3f); lineTo(7f,17f); lineTo(21f,17f); moveTo(3f,7f); lineTo(17f,7f); lineTo(17f,21f) }
                Glyph.Export -> { moveTo(12f,16f); lineTo(12f,3f); moveTo(7f,8f); lineTo(12f,3f); lineTo(17f,8f); moveTo(4f,13f); lineTo(4f,21f); lineTo(20f,21f); lineTo(20f,13f) }
                Glyph.Save -> { moveTo(4f,3f); lineTo(17f,3f); lineTo(21f,7f); lineTo(21f,21f); lineTo(4f,21f); close(); moveTo(8f,3f); lineTo(8f,9f); lineTo(16f,9f); lineTo(16f,3f); moveTo(8f,21f); lineTo(8f,15f); lineTo(17f,15f); lineTo(17f,21f) }
                Glyph.Close -> { moveTo(6f,6f); lineTo(18f,18f); moveTo(18f,6f); lineTo(6f,18f) }
                Glyph.Retry -> { moveTo(20f,10f); curveTo(17f,0f,3f,3f,4f,13f); curveTo(5f,22f,17f,24f,20f,15f); moveTo(20f,4f); lineTo(20f,10f); lineTo(14f,10f) }
                Glyph.Info -> { moveTo(12f,8f); lineTo(12f,8.1f); moveTo(12f,12f); lineTo(12f,17f); moveTo(22f,12f); curveTo(22f,25f,2f,25f,2f,12f); curveTo(2f,-1f,22f,-1f,22f,12f); close() }
                Glyph.Start -> { moveTo(5f,4f); lineTo(5f,20f); moveTo(9f,6f); lineTo(19f,12f); lineTo(9f,18f); close() }
                Glyph.End -> { moveTo(19f,4f); lineTo(19f,20f); moveTo(15f,6f); lineTo(5f,12f); lineTo(15f,18f); close() }
            }
        }
    }.build()
}

@Composable
fun PrismIcon(glyph: Glyph, label: String? = null, tint: Color = LocalContentColor.current) {
    Icon(glyphs.getValue(glyph), label, Modifier.size(24.dp), tint)
}

@Composable
fun GlyphButton(glyph: Glyph, label: String, selected: Boolean = false, enabled: Boolean = true,
                primary: Boolean = false, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.94f else 1f,
        tween(if (LocalPrismMotion.current) 100 else 0), label = "Press feedback")
    FilledTonalIconButton(onClick, modifier = Modifier.size(if (primary) 56.dp else 48.dp).semantics { this.selected = selected }.graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = enabled, interactionSource = interactions,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = when { selected -> Cyan.copy(alpha = 0.18f); primary -> Raised; else -> Color.Transparent },
            contentColor = if (selected || primary) Cyan else Muted)) { PrismIcon(glyph, label) }
}

@Composable
fun PrismAction(label: String, glyph: Glyph, modifier: Modifier = Modifier, enabled: Boolean = true,
                primary: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f,
        tween(if (LocalPrismMotion.current) 100 else 0), label = "Action feedback")
    OutlinedButton(onClick, modifier.heightIn(min = 56.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = enabled, interactionSource = interaction, shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (primary) Cyan.copy(alpha = 0.55f) else Edge),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (primary) Cyan.copy(alpha = 0.12f) else Raised,
            contentColor = if (primary) Cyan else MaterialTheme.colorScheme.onSurface)) {
        PrismIcon(glyph); Spacer(Modifier.width(10.dp)); Text(label)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = Graphite, border = BorderStroke(1.dp, Edge)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
