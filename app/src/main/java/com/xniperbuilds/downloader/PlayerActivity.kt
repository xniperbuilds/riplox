package com.xniperbuilds.downloader

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.xniperbuilds.downloader.ui.theme.XniperDownloaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** "Open with…" — video/audio kisi bhi player/gallery app me kholo (chooser). */
private fun openWith(context: android.content.Context, localUri: String) {
    try {
        val raw = android.net.Uri.parse(localUri)
        // Vault ki file:// → FileProvider (dusri apps file:// nahi le sakti)
        val uri = if (raw.scheme == "file") {
            androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", java.io.File(raw.path!!)
            )
        } else raw
        val mime = context.contentResolver.getType(uri)
            ?: if (localUri.endsWith(".mp3") || localUri.endsWith(".m4a")) "audio/*" else "video/*"
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(i, "Open with"))
    } catch (e: Exception) {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}

private const val PLAYER_UA =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

/**
 * Video player. Do tarah khulta hai:
 *  - "link" extra (ya SEND) → yt-dlp se stream (online preview) + Download button.
 *  - ACTION_VIEW (content uri) → local file play (app video-player ban jata hai).
 * Features: fullscreen, speed, seek (ExoPlayer default controls).
 */
class PlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val localUri = intent?.data?.toString()
        val link = intent.getStringExtra("link")
            ?: if (intent?.action == Intent.ACTION_SEND) extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT)) else null
        if (localUri == null && link.isNullOrBlank()) {
            finish()
            return
        }
        setContent { XniperDownloaderTheme { PlayerScreen(link, localUri) } }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerScreen(link: String?, localUri: String?) {
    val context = LocalContext.current
    val activity = context as? Activity
    var status by remember { mutableStateOf(if (localUri != null) "playing" else "loading") }
    var errMsg by remember { mutableStateOf("") }
    var fullscreen by remember { mutableStateOf(false) }
    var speedIdx by remember { mutableIntStateOf(0) }
    val speeds = remember { floatArrayOf(1f, 1.25f, 1.5f, 2f) }
    val exo = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                errMsg = "${error.errorCodeName}\n${error.message ?: ""}"
                status = "error"
            }
        }
        exo.addListener(listener)
        onDispose { exo.release() }
    }

    LaunchedEffect(link, localUri) {
        try {
            if (localUri != null) {
                exo.setMediaItem(MediaItem.fromUri(localUri))
                exo.prepare()
                exo.playWhenReady = true
                status = "playing"
            } else if (link != null) {
                val media = withContext(Dispatchers.IO) { extractDirect(context, link, false) }
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(PLAYER_UA)
                    .setDefaultRequestProperties(media.headers)
                val source = DefaultMediaSourceFactory(httpFactory)
                    .createMediaSource(MediaItem.fromUri(media.url))
                exo.setMediaSource(source)
                exo.prepare()
                exo.playWhenReady = true
                status = "playing"
            }
        } catch (e: Exception) {
            errMsg = e.message ?: "Error"
            status = "error"
        }
    }

    DisposableEffect(fullscreen) {
        if (activity != null) {
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            if (fullscreen) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }

    // Fullscreen me Back = fullscreen se bahar (poori activity band na ho)
    BackHandler(enabled = fullscreen) { fullscreen = false }

    if (fullscreen) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { PlayerView(it).apply { player = exo; keepScreenOn = true } },
                modifier = Modifier.fillMaxSize()
            )
            OutlinedButton(
                onClick = { fullscreen = false },
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
            ) { Text("Exit ⛶") }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("XniperBuilds — Player", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        when (status) {
            "loading" -> Text("⏳ Loading video… please wait")
            "error" -> Text("❌ Can't play:\n$errMsg", fontSize = 13.sp)
            else -> AndroidView(
                factory = { PlayerView(it).apply { player = exo; keepScreenOn = true } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        if (status == "playing") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { fullscreen = true }, modifier = Modifier.weight(1f)) {
                    Text("⛶ Fullscreen")
                }
                OutlinedButton(
                    onClick = {
                        speedIdx = (speedIdx + 1) % speeds.size
                        exo.setPlaybackSpeed(speeds[speedIdx])
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Speed ${speeds[speedIdx]}x") }
            }
            Spacer(Modifier.height(8.dp))
            if (localUri != null) {
                OutlinedButton(
                    onClick = { openWith(context, localUri) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("↗ Open with…") }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (link != null) {
            Button(
                onClick = {
                    DownloadQueue.enqueue(context, link, false)
                    Toast.makeText(context, "⬇ Download started (check notification)", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = status == "playing"
            ) { Text("⬇ Download") }
        }
    }
}
