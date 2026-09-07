package com.xniperbuilds.downloader

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xniperbuilds.downloader.ui.theme.XniperDownloaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XniperDownloaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { pad ->
                    SettingsScreen(Modifier.padding(pad))
                }
            }
        }
    }
}

// ---------- chhote reusable rows ----------

@Composable
private fun SwitchRow(label: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp)
            if (desc != null) Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DropdownRow(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${options.find { it.first == selected }?.second ?: selected}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (k, l) ->
                DropdownMenuItem(text = { Text(l) }, onClick = { onSelect(k); open = false })
            }
        }
    }
}

// ---------- settings categories ----------

private data class SettingsCat(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val desc: String
)

private val CATS = listOf(
    SettingsCat("downloads", Icons.Outlined.Download, "Downloads", "Subtitles, playlist, parallel downloads"),
    SettingsCat("format", Icons.Outlined.Tune, "Format", "Audio & video quality, container, codec"),
    SettingsCat("accounts", Icons.Outlined.AccountCircle, "Connected accounts", "Logins for private / restricted videos"),
    SettingsCat("network", Icons.Outlined.Wifi, "Network", "Wi-Fi only, speed limit, proxy"),
    SettingsCat("files", Icons.Outlined.Folder, "Files & storage", "Save location, filenames, storage"),
    SettingsCat("look", Icons.Outlined.Palette, "Look & feel", "Theme & colors"),
    SettingsCat("privacy", Icons.Outlined.Lock, "Privacy", "Incognito history"),
    SettingsCat("backup", Icons.Outlined.Backup, "Backup / Restore", "Move to a new phone"),
    SettingsCat("advanced", Icons.Outlined.Bolt, "Advanced", "Power-user options"),
    SettingsCat("about", Icons.Outlined.Info, "About", "Version, share app, engine update")
)

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var page by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = page != null) {
        page = if (page == "cookies") "accounts" else null
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        if (page == null) {
            RiploxHeader(subtitle = "Settings", tileSize = 36)
            Spacer(Modifier.height(16.dp))
            CATS.forEach { c ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clickable {
                            if (c.key == "about") {
                                context.startActivity(Intent(context, AboutActivity::class.java))
                            } else page = c.key
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(c.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(c.title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(c.desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        } else {
            val cat = CATS.find { it.key == page }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    page = if (page == "cookies") "accounts" else null
                }) { Text("← Back", fontSize = 14.sp) }
                Spacer(Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    cat?.icon ?: Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    cat?.title ?: "Saved cookies",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(10.dp))
            when (page) {
                "downloads" -> DownloadsPage()
                "format" -> FormatPage()
                "accounts" -> AccountsPage { page = "cookies" }
                "cookies" -> CookiesPage()
                "network" -> NetworkPage()
                "files" -> FilesPage()
                "look" -> LookPage()
                "privacy" -> PrivacyPage()
                "backup" -> BackupPage()
                "advanced" -> AdvancedPage()
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

// ---------------- ⬇ Downloads ----------------
@Composable
private fun DownloadsPage() {
    val context = LocalContext.current
    var subs by remember { mutableStateOf(Prefs.subs(context)) }
    var playlist by remember { mutableStateOf(Prefs.playlist(context)) }
    var simultaneous by remember { mutableStateOf(Prefs.simultaneous(context)) }
    var showBgSetup by remember { mutableStateOf(false) }

    // Background-setup ka PERMANENT raasta — Home banner "Done" ke baad dobara nahi
    // dikhta (bug-report 2026-07-16: "card nahi dikh raha"), yahan hamesha milega.
    OutlinedButton(onClick = { showBgSetup = true }, modifier = Modifier.fillMaxWidth()) {
        Text("🛡 Fix background downloads")
    }
    Text(
        "Battery + auto-start + recents-lock — so downloads never pause when the app is closed.",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(10.dp))
    if (showBgSetup) {
        BgSetupDialog { showBgSetup = false }
    }

    // ENGINE — download ka asal engine (yt-dlp) roz khud ko update karta hai, magar us ka
    // haal kahin dikhta nahi tha. Jab koi site "kaam karna band" kar de to sabse pehle
    // yehi dekhna hota hai, aur ghair-technical user ke liye ye ek-tap wala jawab hai.
    var engineBusy by remember { mutableStateOf(false) }
    var engineMsg by remember { mutableStateOf(Engine.statusLine(context)) }
    val engineScope = rememberCoroutineScope()
    OutlinedButton(
        onClick = {
            if (engineBusy) return@OutlinedButton
            if (DownloadQueue.hasActive(context)) {
                engineMsg = "A download is running — update after it finishes."
                return@OutlinedButton
            }
            engineBusy = true
            engineMsg = "Updating… (~10 MB)"
            engineScope.launch {
                engineMsg = when (Engine.update(context)) {
                    Engine.Outcome.UPDATED -> "✓ Engine updated (${Prefs.engineVersion(context)})"
                    Engine.Outcome.ALREADY_LATEST -> "✓ Already the latest (${Prefs.engineVersion(context)})"
                    Engine.Outcome.FAILED -> "Update failed — check internet, then try again."
                }
                engineBusy = false
            }
        },
        enabled = !engineBusy,
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (engineBusy) "Updating…" else "⚙ Update download engine") }
    Text(engineMsg, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))

    // Note: video/audio, quality, subs, playlist — download popup me choose hote hi SAVE
    // ho jate hain (wahi defaults ban jate hain). Isliye yahan extra toggles nahi.
    SwitchRow("Subtitles", "Embed English subtitles when available", subs) {
        subs = it; Prefs.setSubs(context, it)
    }
    SwitchRow("Full playlist", "Download every video in a playlist link", playlist) {
        playlist = it; Prefs.setPlaylist(context, it)
    }
    Spacer(Modifier.height(8.dp))
    Text("Parallel downloads: $simultaneous", fontSize = 14.sp)
    Text(
        "Background downloads running at once (1–5)",
        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Slider(
        value = simultaneous.toFloat(),
        onValueChange = { simultaneous = it.toInt(); Prefs.setSimultaneous(context, it.toInt()) },
        valueRange = 1f..5f,
        steps = 3,
        modifier = Modifier.fillMaxWidth()
    )
    var maxRetries by remember { mutableStateOf(Prefs.maxRetries(context)) }
    Text("Retry attempts: $maxRetries", fontSize = 14.sp)
    Text(
        "How many times a failed download is tried in total (1–5)",
        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Slider(
        value = maxRetries.toFloat(),
        onValueChange = { maxRetries = it.toInt(); Prefs.setMaxRetries(context, it.toInt()) },
        valueRange = 1f..5f,
        steps = 3,
        modifier = Modifier.fillMaxWidth()
    )
}

// ---------------- 🎞 Format ----------------
@Composable
private fun FormatPage() {
    val context = LocalContext.current
    var audioFormat by remember { mutableStateOf(Prefs.audioFormat(context)) }
    var audioQuality by remember { mutableStateOf(Prefs.audioQuality(context)) }
    var videoContainer by remember { mutableStateOf(Prefs.videoContainer(context)) }
    var videoCodec by remember { mutableStateOf(Prefs.videoCodec(context)) }
    var mergeAudio by remember { mutableStateOf(Prefs.mergeAudio(context)) }
    var embedThumbVideo by remember { mutableStateOf(Prefs.embedThumbVideo(context)) }
    var embedChapters by remember { mutableStateOf(Prefs.embedChapters(context)) }

    DropdownRow(
        "Audio format",
        listOf("best" to "Best (original)", "mp3" to "MP3", "m4a" to "M4A", "opus" to "OPUS", "wav" to "WAV", "flac" to "FLAC"),
        audioFormat
    ) { audioFormat = it; Prefs.setAudioFormat(context, it) }
    DropdownRow(
        "Audio quality",
        listOf("best" to "Best", "320" to "320 kbps", "256" to "256 kbps", "192" to "192 kbps", "128" to "128 kbps"),
        audioQuality
    ) { audioQuality = it; Prefs.setAudioQuality(context, it) }
    DropdownRow(
        "Video container",
        listOf("mp4" to "MP4", "mkv" to "MKV", "webm" to "WEBM"),
        videoContainer
    ) { videoContainer = it; Prefs.setVideoContainer(context, it) }
    DropdownRow(
        "Video codec",
        listOf("any" to "Any (best)", "h264" to "H.264 (max compatibility)", "vp9" to "VP9", "av1" to "AV1 (smallest)"),
        videoCodec
    ) { videoCodec = it; Prefs.setVideoCodec(context, it) }
    SwitchRow("Merge audio streams", "Multiple audio tracks with the video", mergeAudio) {
        mergeAudio = it; Prefs.setMergeAudio(context, it)
    }
    SwitchRow("Embed thumbnail in video", "Cover image inside the video", embedThumbVideo) {
        embedThumbVideo = it; Prefs.setEmbedThumbVideo(context, it)
    }
    SwitchRow("Embed chapters", "Chapters (if present in the video)", embedChapters) {
        embedChapters = it; Prefs.setEmbedChapters(context, it)
    }
}

// ---------------- 🔗 Connected accounts ----------------
@Composable
private fun AccountsPage(openCookies: () -> Unit = {}) {
    val context = LocalContext.current
    var cookiesOn by remember { mutableStateOf(Prefs.cookiesEnabled(context)) }
    var ckSites by remember { mutableStateOf(cookieSites(context)) }
    var msg by remember { mutableStateOf("") }
    var showCustomLogin by remember { mutableStateOf(false) }
    var customLoginUrl by remember { mutableStateOf("") }

    val cookieLogin = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        ckSites = cookieSites(context)
        if (hasCookies(context)) msg = "✓ Connected — restricted videos from that site will work now."
    }
    fun startLogin(site: String, label: String, url: String? = null) {
        val i = Intent(context, CookieLoginActivity::class.java)
            .putExtra("site", site)
            .putExtra("label", label)
        if (url != null) i.putExtra("url", url)
        cookieLogin.launch(i)
    }
    val cookiePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val ok = importCookies(context, uri)
            ckSites = cookieSites(context)
            msg = if (ok) "✓ File imported — restricted videos will work now."
            else "❌ Import failed — that's not a valid cookies.txt file."
        }
    }

    Text(
        "Private or restricted videos need a login. You sign in on the real site, inside the app. 🔒 Your password never touches this app — nothing leaves your phone.",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    SwitchRow("Use connected accounts", null, cookiesOn) {
        cookiesOn = it; Prefs.setCookiesEnabled(context, it)
    }
    Spacer(Modifier.height(4.dp))
    if (ckSites.isNotEmpty()) {
        ckSites.forEach { s ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("🔗 $s", modifier = Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = {
                    disconnectSite(context, s)
                    ckSites = cookieSites(context)
                    msg = "$s disconnected — its login data deleted from this phone."
                }) { Text("Disconnect") }
            }
        }
        if (ckSites.size > 1) {
            TextButton(onClick = {
                clearCookies(context); ckSites = emptyList()
                msg = "All accounts disconnected."
            }) { Text("Disconnect all", fontSize = 12.sp) }
        }
        Spacer(Modifier.height(6.dp))
    }
    Text("Connect a site:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { startLogin("youtube", "YouTube") }, modifier = Modifier.weight(1f)) {
            Icon(painterResource(R.drawable.ic_logo_youtube), contentDescription = "YouTube", modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        }
        OutlinedButton(onClick = { startLogin("instagram", "Instagram") }, modifier = Modifier.weight(1f)) {
            Icon(painterResource(R.drawable.ic_logo_instagram), contentDescription = "Instagram", modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        }
        OutlinedButton(onClick = { startLogin("tiktok", "TikTok") }, modifier = Modifier.weight(1f)) {
            Icon(painterResource(R.drawable.ic_logo_tiktok), contentDescription = "TikTok", modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        }
        OutlinedButton(onClick = { startLogin("facebook", "Facebook") }, modifier = Modifier.weight(1f)) {
            Icon(painterResource(R.drawable.ic_logo_facebook), contentDescription = "Facebook", modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { showCustomLogin = true }, modifier = Modifier.weight(1f)) { Text("Other site") }
        OutlinedButton(onClick = { cookiePicker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) { Text("Import file") }
    }
    if (msg.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(msg, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(10.dp))
    OutlinedButton(onClick = openCookies, modifier = Modifier.fillMaxWidth()) {
        Text("Saved cookies →")
    }

    if (showCustomLogin) {
        AlertDialog(
            onDismissRequest = { showCustomLogin = false },
            title = { Text("Custom site login") },
            text = {
                Column {
                    Text("Enter the site whose login you need (https:// is added for you):", fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customLoginUrl,
                        onValueChange = {
                            customLoginUrl = it.removePrefix("https://").removePrefix("http://")
                        },
                        singleLine = true,
                        prefix = { Text("https://") },
                        label = { Text("example.com/login") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val u = customLoginUrl.trim().removePrefix("https://").removePrefix("http://")
                    if (u.isNotBlank()) {
                        showCustomLogin = false
                        startLogin("custom", "Custom", "https://$u")
                    }
                }) { Text("Open") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomLogin = false }) { Text("Cancel") }
            }
        )
    }
}

// ---------------- 🌐 Network ----------------
@Composable
private fun NetworkPage() {
    val context = LocalContext.current
    var wifiOnly by remember { mutableStateOf(Prefs.wifiOnly(context)) }
    var rateLimit by remember { mutableStateOf(Prefs.rateLimit(context)) }
    var multiThread by remember { mutableStateOf(Prefs.multiThread(context)) }
    var turbo by remember { mutableStateOf(Prefs.turbo(context)) }
    var forceIpv4 by remember { mutableStateOf(Prefs.forceIpv4(context)) }
    var proxy by remember { mutableStateOf(Prefs.proxy(context)) }

    SwitchRow("Wi-Fi only", "Block downloads on mobile data (save data)", wifiOnly) {
        wifiOnly = it; Prefs.setWifiOnly(context, it)
    }
    DropdownRow(
        "Rate limit (speed)",
        listOf("0" to "Off (max speed)", "500K" to "500 KB/s", "1M" to "1 MB/s", "2M" to "2 MB/s", "5M" to "5 MB/s"),
        rateLimit
    ) { rateLimit = it; Prefs.setRateLimit(context, it) }
    SwitchRow("Multi-thread download", "Concurrent fragments — faster (default ON)", multiThread) {
        multiThread = it; Prefs.setMultiThread(context, it)
    }
    SwitchRow("Turbo (aria2c engine)", "Multi-connection download — much faster on direct files. Turn off if a site misbehaves.", turbo) {
        turbo = it; Prefs.setTurbo(context, it)
    }
    SwitchRow("Force IPv4", "Force all connections over IPv4", forceIpv4) {
        forceIpv4 = it; Prefs.setForceIpv4(context, it)
    }
    OutlinedTextField(
        value = proxy,
        onValueChange = { proxy = it; Prefs.setProxy(context, it) },
        label = { Text("Proxy (blank = off)") },
        placeholder = { Text("socks5://127.0.0.1:1080") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

// ---------------- 📁 Files & storage ----------------
@Composable
private fun FilesPage() {
    val context = LocalContext.current
    var restrictNames by remember { mutableStateOf(Prefs.restrictNames(context)) }
    var downloadArchive by remember { mutableStateOf(Prefs.downloadArchive(context)) }
    var saveThumb by remember { mutableStateOf(Prefs.saveThumb(context)) }
    var template by remember { mutableStateOf(Prefs.filenameTemplate(context)) }
    var storageText by remember { mutableStateOf("Calculating…") }
    var storageRefresh by remember { mutableStateOf(0) }
    var customLoc by remember { mutableStateOf(Prefs.customLocationUri(context)) }
    var confirmTempClear by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                Prefs.setCustomLocationUri(context, uri.toString())
                customLoc = uri.toString()
                Toast.makeText(context, "✓ Custom folder set", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "❌ Couldn't set folder: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(storageRefresh) {
        val s = withContext(Dispatchers.IO) { storageUsage(context) }
        storageText = "🎬 ${s.videoCount} videos (${humanBytes(s.videoBytes)}) · " +
            "🎵 ${s.audioCount} audio (${humanBytes(s.audioBytes)}) · " +
            "🗂 temp ${humanBytes(s.tempBytes)}\nTotal: ${humanBytes(s.totalBytes)}"
    }

    Text("📂 Download location", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
    Text(
        if (customLoc.isBlank()) "Gallery default (Movies / Music → XniperBuilds)"
        else "Custom: ${treeDisplayName(context, android.net.Uri.parse(customLoc))}",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.weight(1f)) {
            Text("Choose folder / SD")
        }
        if (customLoc.isNotBlank()) {
            OutlinedButton(onClick = {
                Prefs.setCustomLocationUri(context, ""); customLoc = ""
                Toast.makeText(context, "Gallery default set", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.weight(1f)) { Text("Reset") }
        }
    }
    Spacer(Modifier.height(10.dp))
    SwitchRow("Restrict filenames", "Only safe characters (compatibility)", restrictNames) {
        restrictNames = it; Prefs.setRestrictNames(context, it)
    }
    SwitchRow("Download archive", "Skip already-downloaded videos", downloadArchive) {
        downloadArchive = it; Prefs.setDownloadArchive(context, it)
    }
    SwitchRow("Save thumbnail as file", "Also save thumbnail as a separate image", saveThumb) {
        saveThumb = it; Prefs.setSaveThumb(context, it)
    }
    OutlinedTextField(
        value = template,
        onValueChange = { template = it; Prefs.setFilenameTemplate(context, it) },
        label = { Text("Filename template (blank = default)") },
        placeholder = { Text("%(uploader)s_%(title)s.%(ext)s") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
    Spacer(Modifier.height(6.dp))
    fun doTempClear() {
        val freed = clearTempFiles(context)
        Toast.makeText(context, "✓ Temp clear — ${humanBytes(freed)} freed", Toast.LENGTH_SHORT).show()
        storageRefresh++
    }
    if (confirmTempClear) {
        AlertDialog(
            onDismissRequest = { confirmTempClear = false },
            title = { Text("Downloads are running") },
            text = { Text("Clearing temp now will make the active downloads fail. Clear anyway?") },
            confirmButton = {
                TextButton(onClick = { confirmTempClear = false; doTempClear() }) { Text("Clear anyway") }
            },
            dismissButton = { TextButton(onClick = { confirmTempClear = false }) { Text("Cancel") } }
        )
    }
    OutlinedButton(
        onClick = {
            // Active download ka temp folder bhi uda deta — pehle warn karo
            if (DownloadQueue.hasActive(context)) confirmTempClear = true else doTempClear()
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("🧹 Clear temp / cache") }
    Spacer(Modifier.height(10.dp))
    Text("📊 Storage usage", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
    Text(storageText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = { storageText = "Calculating…"; storageRefresh++ }, modifier = Modifier.fillMaxWidth()) {
        Text("↻ Refresh storage")
    }
}

// ---------------- 🎨 Look & feel ----------------
@Composable
private fun LookPage() {
    val context = LocalContext.current
    val activity = context as? Activity
    var themeMode by remember { mutableStateOf(Prefs.themeMode(context)) }
    var dynamicColor by remember { mutableStateOf(Prefs.dynamicColor(context)) }

    DropdownRow(
        "Theme",
        listOf("system" to "System default", "light" to "Light", "dark" to "Dark", "amoled" to "AMOLED (pure black)"),
        themeMode
    ) { themeMode = it; Prefs.setThemeMode(context, it); activity?.recreate() }
    SwitchRow("Dynamic color (Material You)", "Colors from your wallpaper (Android 12+)", dynamicColor) {
        dynamicColor = it; Prefs.setDynamicColor(context, it); activity?.recreate()
    }
}

// ---------------- 🔒 Privacy ----------------
@Composable
private fun PrivacyPage() {
    val context = LocalContext.current
    var incognito by remember { mutableStateOf(Prefs.incognito(context)) }
    var vault by remember { mutableStateOf(Prefs.vaultEnabled(context)) }
    SwitchRow("Incognito", "Don't save download history", incognito) {
        incognito = it; Prefs.setIncognito(context, it)
    }
    SwitchRow("Secret vault", "Tap the home logo to open a hidden, locked vault", vault) {
        vault = it; Prefs.setVaultEnabled(context, it)
    }
}

// ---------------- 🍪 Saved cookies (archive) ----------------
@Composable
private fun CookiesPage() {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val sites = remember(refresh) { archivedSites(context) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var deleteAll by remember { mutableStateOf(false) }

    Text(
        "When you disconnect an account, its cookies land here (the account stays disconnected). Delete them whenever you want.",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(10.dp))

    if (sites.isEmpty()) {
        Text("Nothing saved.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        sites.forEach { s ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("🍪 $s", modifier = Modifier.weight(1f), fontSize = 13.sp)
                TextButton(onClick = { deleteTarget = s }) { Text("Delete") }
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = { deleteAll = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Delete all")
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${deleteTarget} cookies?") },
            text = { Text("Permanent. Next time you'll have to log in to $deleteTarget again to download its private videos. Nothing else is affected — your real account stays safe.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteArchivedSite(context, deleteTarget!!)
                    deleteTarget = null; refresh++
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
    if (deleteAll) {
        AlertDialog(
            onDismissRequest = { deleteAll = false },
            title = { Text("Delete ALL saved cookies?") },
            text = { Text("Permanent. You'll need to log in again on each site for private videos. Your real accounts are not touched.") },
            confirmButton = {
                TextButton(onClick = {
                    clearArchive(context); deleteAll = false; refresh++
                }) { Text("Delete all") }
            },
            dismissButton = { TextButton(onClick = { deleteAll = false }) { Text("Cancel") } }
        )
    }
}

// ---------------- 💾 Backup ----------------
@Composable
private fun BackupPage() {
    val context = LocalContext.current
    val activity = context as? Activity
    var msg by remember { mutableStateOf("") }

    val backupSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            msg = try {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(Backup.export(context).toByteArray())
                }
                "✓ Backup saved."
            } catch (e: Exception) {
                "❌ Backup failed: ${e.message}"
            }
        }
    }
    val backupRestorer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            msg = try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                if (Backup.import(context, text)) {
                    activity?.recreate()
                    "✓ Restored — settings + history + accounts are back."
                } else "❌ Restore failed — choose a valid backup file."
            } catch (e: Exception) {
                "❌ Restore failed: ${e.message}"
            }
        }
    }

    Text(
        "Save settings + history + connected accounts to one file (restore on a new phone / reinstall). ⚠️ Includes your login sessions — keep the file private.",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = { backupSaver.launch("Riplox_backup.json") },
            modifier = Modifier.weight(1f)
        ) { Text("💾 Backup") }
        OutlinedButton(
            onClick = { backupRestorer.launch(arrayOf("application/json", "text/*", "*/*")) },
            modifier = Modifier.weight(1f)
        ) { Text("↺ Restore") }
    }
    if (msg.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(msg, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
    }
}

// ---------------- ⚡ Advanced ----------------
@Composable
private fun AdvancedPage() {
    val context = LocalContext.current
    var trashPurge by remember { mutableStateOf(Prefs.trashPurgeDays(context)) }
    var playlistRange by remember { mutableStateOf(Prefs.playlistRange(context)) }
    var customFlags by remember { mutableStateOf(Prefs.customFlags(context)) }
    var sponsorBlock by remember { mutableStateOf(Prefs.sponsorBlock(context)) }

    DropdownRow(
        "Trash auto-purge",
        listOf("0" to "Off", "7" to "After 7 days", "14" to "After 14 days", "30" to "After 30 days"),
        trashPurge
    ) { trashPurge = it; Prefs.setTrashPurgeDays(context, it) }
    OutlinedTextField(
        value = playlistRange,
        onValueChange = { playlistRange = it; Prefs.setPlaylistRange(context, it) },
        label = { Text("Playlist range (when playlist is ON)") },
        placeholder = { Text("1-10 or 3,5,7 — blank = all") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
    OutlinedTextField(
        value = customFlags,
        onValueChange = { customFlags = it; Prefs.setCustomFlags(context, it) },
        label = { Text("Custom yt-dlp flags (power users)") },
        placeholder = { Text("--sleep-interval 5 --no-mtime") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
    SwitchRow("SponsorBlock", "Remove YouTube sponsor segments from the video", sponsorBlock) {
        sponsorBlock = it; Prefs.setSponsorBlock(context, it)
    }
}
