package com.itu.videoeditor.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itu.videoeditor.ItuApplication
import com.itu.videoeditor.data.ProjectEntity
import com.itu.videoeditor.ui.*
import kotlinx.coroutines.CancellationException

@Composable
fun MediaThumbnail(project: ProjectEntity, modifier: Modifier = Modifier) {
    val store = (LocalContext.current.applicationContext as ItuApplication).thumbnails
    var attempt by remember(project.sourceUri, project.trimStartUs) { mutableIntStateOf(0) }
    var bitmap by remember(project.sourceUri, project.trimStartUs) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var error by remember(project.sourceUri, project.trimStartUs) { mutableStateOf(false) }
    LaunchedEffect(project.sourceUri, project.trimStartUs, attempt) {
        error = false; bitmap = null
        try { bitmap = store.load(project, refresh = attempt > 0) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = true }
    }
    Box(modifier.background(Raised), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), "Video thumbnail: ${project.sourceName}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        if (bitmap == null && !error) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        if (error) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlyphButton(Glyph.Retry, "Retry thumbnail for ${project.sourceName}", onClick = { attempt++ })
            Text("Thumbnail unavailable", color = Muted, fontSize = 10.sp, textAlign = TextAlign.Center)
        }
    }
}
