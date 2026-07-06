package com.xniperbuilds.downloader

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xniperbuilds.downloader.ui.theme.XniperDownloaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "🎛 Download Options" — share tile + home tile dono se khulta hai.
 * IS download ke liye options chun ke background queue me daalo — popup sirf choose
 * karne ke liye, wait ke liye nahi. (Riplox brand card.)
 */
class ConfigureDownloadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Share-sheet (ACTION_SEND) ya home ka 🎛 Options tile (extra "link") — dono raste
        val link = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
        } else {
            intent?.getStringExtra("link")
        }

        if (link.isNullOrBlank() || !link.startsWith("http")) {
            Toast.makeText(this, "No link found in the shared text", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            XniperDownloaderTheme {
                ConfigureSheet(link) { finish() }
            }
        }
    }
}

@Composable
private fun ConfigureSheet(link: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val scope = rememberCoroutineScope()
    var queued by remember { mutableStateOf(false) }
    var isAudio by remember { mutableStateOf(Prefs.audioMode(context)) }
    var quality by remember { mutableStateOf(Prefs.quality(context)) }
    var playlist by remember { mutableStateOf(Prefs.playlist(context)) }
    var subs by remember { mutableStateOf(Prefs.subs(context)) }
    var audioFmt by remember { mutableStateOf(Prefs.audioFormat(context)) }
    var afMenu by remember { mutableStateOf(false) }
    var pickedFormat by remember { mutableStateOf<FormatOption?>(null) }
    var formats by remember { mutableStateOf<List<FormatOption>>(emptyList()) }
    var showFormats by remember { mutableStateOf(false) }
    var loadingFormats by remember { mutableStateOf(false) }
    var pv by remember { mutableStateOf<Preview?>(null) }
    val qualities = listOf(
        "best" to "Best", "2160" to "4K", "1080" to "1080p",
        "720" to "720p", "480" to "480p", "360" to "360p"
    )
    val audioFormats = listOf(
        "best" to "Best", "mp3" to "MP3", "m4a" to "M4A",
        "opus" to "OPUS", "wav" to "WAV", "flac" to "FLAC"
    )

    // Thumbnail/title PARALLEL — card turant khulta, preview aa jaye to dikha do
    LaunchedEffect(link) {
        pv = withContext(Dispatchers.IO) {
            try { getPreview(context, link) } catch (e: Exception) { null }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
                RiploxHeader(subtitle = "Download options", tileSize = 34)
                Spacer(Modifier.height(10.dp))

                // ---- Preview (thumbnail + title) ya link ----
                if (pv != null) {
                    val p = pv!!
                    if (!p.thumbnail.isNullOrBlank()) {
                        AsyncImage(
                            model = p.thumbnail,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(p.title, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(
                        link,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))

                // ---- Type: Video / Audio ----
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!isAudio) {
                        Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("🎬 Video ✓") }
                        OutlinedButton(onClick = { isAudio = true }, modifier = Modifier.weight(1f)) { Text("🎵 Audio") }
                    } else {
                        OutlinedButton(onClick = { isAudio = false }, modifier = Modifier.weight(1f)) { Text("🎬 Video") }
                        Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("🎵 Audio ✓") }
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (!isAudio) {
                    // ---- Quality chips ----
                    Text("Quality", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        qualities.forEach { (k, label) ->
                            FilterChip(
                                selected = quality == k && pickedFormat == null,
                                onClick = { quality = k; pickedFormat = null },
                                label = { Text(label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // ---- Exact format (optional) ----
                    OutlinedButton(
                        onClick = {
                            if (loadingFormats) return@OutlinedButton
                            loadingFormats = true
                            scope.launch {
                                try {
                                    val f = withContext(Dispatchers.IO) { fetchFormats(context, link) }
                                    formats = f
                                    if (f.isEmpty()) {
                                        Toast.makeText(context, "No format list for this link", Toast.LENGTH_SHORT).show()
                                    } else showFormats = true
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Couldn't load formats", Toast.LENGTH_SHORT).show()
                                }
                                loadingFormats = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            when {
                                loadingFormats -> "⏳ Loading formats…"
                                pickedFormat != null -> "✓ ${pickedFormat!!.label()}"
                                else -> "📐 Exact format / size (optional)"
                            },
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    // ---- Toggles ----
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Full playlist", Modifier.weight(1f), fontSize = 13.sp)
                        Switch(checked = playlist, onCheckedChange = { playlist = it })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Subtitles (embed)", Modifier.weight(1f), fontSize = 13.sp)
                        Switch(checked = subs, onCheckedChange = { subs = it })
                    }
                } else {
                    // ---- Audio options ----
                    Text("Audio format", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { afMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("🎵 ${audioFormats.find { it.first == audioFmt }?.second ?: "Best"}  ▾")
                        }
                        DropdownMenu(expanded = afMenu, onDismissRequest = { afMenu = false }) {
                            audioFormats.forEach { (k, l) ->
                                DropdownMenuItem(text = { Text(l) }, onClick = { audioFmt = k; afMenu = false })
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Full playlist", Modifier.weight(1f), fontSize = 13.sp)
                        Switch(checked = playlist, onCheckedChange = { playlist = it })
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onClose, enabled = !queued, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    GradientButton(
                        text = if (queued) "⏳ Starting…" else "⬇ Download",
                        enabled = !queued,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (queued) return@GradientButton
                        queued = true
                        // Popup ki choices = naye defaults (Instant tile bhi aage yehi use karega)
                        Prefs.setAudioMode(context, isAudio)
                        Prefs.setPlaylist(context, playlist)
                        if (isAudio) {
                            Prefs.setAudioFormat(context, audioFmt)
                        } else {
                            Prefs.setQuality(context, quality)
                            Prefs.setSubs(context, subs)
                        }
                        val workId = DownloadQueue.enqueue(
                            context, link, isAudio,
                            quality = if (isAudio) null else quality,
                            playlist = playlist,
                            subs = if (isAudio) null else subs,
                            format = if (isAudio) null else pickedFormat?.formatArg(),
                            audioFormat = if (isAudio) audioFmt else null
                        )
                        // AIRLOCK: worker ke start-confirm tak card zinda — phir band
                        if (activity != null) {
                            DownloadQueue.awaitStart(activity, workId) { started ->
                                Toast.makeText(
                                    context,
                                    if (started) "⬇ Download started — check notification"
                                    else "⬇ Queued — starts when network/queue allows",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onClose()
                            }
                        } else {
                            Toast.makeText(context, "⬇ Download started — check notification", Toast.LENGTH_SHORT).show()
                            onClose()
                        }
                    }
                }
            }
        }
    }

    if (showFormats) {
        AlertDialog(
            onDismissRequest = { showFormats = false },
            title = { Text("Pick exact format") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    formats.forEach { f ->
                        OutlinedButton(
                            onClick = { pickedFormat = f; showFormats = false },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        ) { Text(f.label(), fontSize = 12.sp) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFormats = false }) { Text("Close") }
            }
        )
    }
}
