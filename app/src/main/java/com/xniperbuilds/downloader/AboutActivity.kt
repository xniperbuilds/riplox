package com.xniperbuilds.downloader

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.xniperbuilds.downloader.ui.theme.XniperDownloaderTheme
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XniperDownloaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { p ->
                    AboutScreen(Modifier.padding(p))
                }
            }
        }
    }
}

@Composable
private fun AboutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    } catch (e: Exception) {
        "1.0"
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        RiploxTile(60)
        Spacer(Modifier.height(10.dp))
        Text("Riplox", fontSize = 30.sp, color = MaterialTheme.colorScheme.primary)
        Text("v$version · by XniperBuilds", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            "Video & audio downloader — free, no ads.",
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        var updating by remember { mutableStateOf(false) }
        var updateMsg by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()
        OutlinedButton(
            onClick = {
                if (updating) return@OutlinedButton
                if (DownloadQueue.hasActive(context)) {
                    updateMsg = "A download is running — update after it finishes."
                    return@OutlinedButton
                }
                updating = true
                updateMsg = "Updating… (~10 MB)"
                scope.launch {
                    updateMsg = try {
                        withContext(Dispatchers.IO) {
                            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                        }
                        val v = withContext(Dispatchers.IO) { YoutubeDL.getInstance().version(context) }
                        "✓ Engine updated ($v)"
                    } catch (e: Exception) {
                        "Update failed — check internet."
                    }
                    updating = false
                }
            },
            enabled = !updating,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (updating) "Updating…" else "Update engine") }
        if (updateMsg.isNotBlank()) {
            Text(updateMsg, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))

        Button(
            onClick = { shareApp(context) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Share app") }
        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = { shareApkFile(context) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Share APK (offline)") }
        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = { context.startActivity(Intent(context, ServicesActivity::class.java)) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("More from XniperBuilds") }
        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { feedback(context, bug = false, version) },
                modifier = Modifier.weight(1f)
            ) { Text("Request feature") }
            OutlinedButton(
                onClick = { feedback(context, bug = true, version) },
                modifier = Modifier.weight(1f)
            ) { Text("Report bug") }
        }
        Spacer(Modifier.height(24.dp))

        Text(
            "Powered by yt-dlp · FFmpeg · ExoPlayer",
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
    }
}

/** Feature-request / bug-report — GitHub Issues (primary), fail ho to email. */
private fun feedback(context: android.content.Context, bug: Boolean, version: String) {
    val label = if (bug) "bug" else "enhancement"
    val title = if (bug) "[Bug] " else "[Feature] "
    val ghUrl = "https://github.com/xniperbuilds/riplox/issues/new?labels=$label&title=" +
        android.net.Uri.encode(title)
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(ghUrl)))
    } catch (e: Exception) {
        try {
            val body = "\n\n—\nRiplox v$version · Android ${android.os.Build.VERSION.RELEASE}"
            val mail = Intent(
                Intent.ACTION_SENDTO,
                android.net.Uri.parse(
                    "mailto:hello@xnipertools.com?subject=" +
                        android.net.Uri.encode("Riplox ${if (bug) "bug" else "feature request"}") +
                        "&body=" + android.net.Uri.encode(body)
                )
            )
            context.startActivity(mail)
        } catch (e2: Exception) {
            Toast.makeText(context, "No browser/email app found", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun shareApp(context: android.content.Context) {
    val text = "Riplox — download video & audio from any site, free.\nhttps://xniperbuilds.com"
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(i, "Share app"))
}

/** App ki apni APK ko share karo (WhatsApp / Bluetooth / Nearby) — bina net install ho jaye. */
private fun shareApkFile(context: android.content.Context) {
    try {
        val src = File(context.applicationInfo.sourceDir)
        val dir = File(context.cacheDir, "apk_share").apply { mkdirs() }
        val dest = File(dir, "Riplox.apk")
        // Wahi APK pehle se copy hai to dobara copy na karo (APK bari hoti hai)
        if (!dest.exists() || dest.length() != src.length()) {
            src.copyTo(dest, overwrite = true)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(i, "Share APK"))
    } catch (e: Exception) {
        Toast.makeText(context, "Couldn't share APK: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
