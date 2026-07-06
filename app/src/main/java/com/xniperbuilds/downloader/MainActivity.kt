package com.xniperbuilds.downloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import com.xniperbuilds.downloader.ui.theme.SpaceGrotesk
import com.xniperbuilds.downloader.ui.theme.XniperDownloaderTheme
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Notification permission (Android 13+) — download progress dikhane ke liye
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001
            )
        }

        // Android 8/9 (API < 29): gallery-save ke liye storage permission chahiye
        if (Build.VERSION.SDK_INT < 29 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1002
            )
        }

        // Battery/data-saver system screens onboarding ke BAAD (pehle open pe confuse na karein)
        // Background download reliable ho — battery optimization exemption ek dafa maango
        if (Prefs.onboarded(this) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Prefs.askedBattery(this)
        ) {
            val pm = getSystemService(PowerManager::class.java)
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    @Suppress("BatteryLife")
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:$packageName")
                        )
                    )
                } catch (_: Exception) {
                }
            }
            Prefs.setAskedBattery(this, true)
        }

        // Data Saver on ho to background me network band hota hai → download atak jata.
        // Ek dafa "unrestricted data" settings khol ke user se allow karwao.
        if (Prefs.onboarded(this) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !Prefs.askedDataSaver(this)
        ) {
            val cm = getSystemService(ConnectivityManager::class.java)
            if (cm != null &&
                cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
            ) {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
                            Uri.parse("package:$packageName")
                        )
                    )
                } catch (_: Exception) {
                }
            }
            Prefs.setAskedDataSaver(this, true)
        }

        enableEdgeToEdge()
        setContent {
            XniperDownloaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    HomeScreen(Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var ckSites by remember { mutableStateOf(connectedSites(context)) }
    var recent by remember { mutableStateOf<List<DownloadRecord>>(emptyList()) }
    val cookieLogin = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // ✓ sirf REAL login pe (CookieLoginActivity khud validate + toast karti hai)
        ckSites = connectedSites(context)
    }
    fun startLogin(site: String, label: String) {
        cookieLogin.launch(
            Intent(context, CookieLoginActivity::class.java)
                .putExtra("site", site)
                .putExtra("label", label)
        )
    }
    var onboarded by remember { mutableStateOf(Prefs.onboarded(context)) }

    // Download = hamesha Options-popup (wahan video/audio/quality choose + choices save hoti hain)
    fun openDownloadPopup() {
        val l = url.trim()
        if (l.isBlank() || !l.startsWith("http")) {
            Toast.makeText(context, "Paste a link first", Toast.LENGTH_SHORT).show()
            return
        }
        context.startActivity(
            Intent(context, ConfigureDownloadActivity::class.java).putExtra("link", l)
        )
        url = ""
    }

    // Din me ek dafa engine auto-update (app open pe, background me chupchaap).
    // Guard: (a) koi download chal rahi ho to skip — binary mid-download replace na ho;
    // (b) Wi-Fi-only ON + mobile data → skip (user ka data na jale).
    LaunchedEffect(Unit) {
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            .format(java.util.Date())
        if (Prefs.lastUpdateDay(context) != today) {
            try {
                val skip = withContext(Dispatchers.IO) {
                    DownloadQueue.hasActive(context) ||
                        (Prefs.wifiOnly(context) && isMetered(context))
                }
                if (!skip) {
                    withContext(Dispatchers.IO) {
                        YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                    }
                    Prefs.setLastUpdateDay(context, today)
                }
            } catch (_: Exception) {
            }
        }
    }

    // App open pe: purani trash auto-purge + clipboard me link ho to suggest
    LaunchedEffect(Unit) {
        val days = Prefs.trashPurgeDays(context).toIntOrNull() ?: 0
        if (days > 0) History.purgeOldTrash(context, days)
        if (url.isBlank()) {
            try {
                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                val clip = cm?.primaryClip?.getItemAt(0)?.text?.toString()
                val link = extractUrl(clip)
                if (!link.isNullOrBlank() && link.startsWith("http") && link != Prefs.lastClip(context)) {
                    url = link
                    Prefs.setLastClip(context, link)
                    Toast.makeText(context, "📋 Link pasted from clipboard", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
            }
        }
    }

    // Recent + ✓ — app open AUR har wapsi (ON_RESUME) pe refresh
    // (vault-move / history-delete ke baad Recent stale na rahe)
    var refreshTick by remember { mutableStateOf(0) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(refreshTick) {
        recent = withContext(Dispatchers.IO) { History.all(context).take(3) }
        ckSites = connectedSites(context)
    }

    if (!onboarded) {
        OnboardingScreen(modifier) {
            Prefs.setOnboarded(context, true)
            onboarded = true
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { context.startActivity(Intent(context, TipsActivity::class.java)) }) {
                Text("💡 Tips", fontSize = 13.sp)
            }
            Icon(
                Icons.Outlined.Download,
                contentDescription = "Downloads",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { context.startActivity(Intent(context, DownloadsActivity::class.java)) }
                    .padding(8.dp)
                    .size(22.dp)
            )
            Icon(
                Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { context.startActivity(Intent(context, SettingsActivity::class.java)) }
                    .padding(8.dp)
                    .size(22.dp)
            )
        }
        Spacer(Modifier.height(22.dp))
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Logo = secret vault ka chhupa button (Settings → Privacy se off ho sakta)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        if (Prefs.vaultEnabled(context)) {
                            context.startActivity(Intent(context, VaultActivity::class.java))
                        }
                    }
            ) { RiploxTile(64) }
            Spacer(Modifier.height(12.dp))
            Text(
                "Riplox",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SpaceGrotesk,
                letterSpacing = (-1).sp,
                color = Color(0xFFDDE4EF)
            )
        }
        Spacer(Modifier.height(28.dp))

        // Notification band ho to downloads ki progress kahin nazar nahi aati → user ko
        // lagta "app kaam nahi karti". Banner + ek tap se on karne ka rasta.
        var notifOff by remember {
            mutableStateOf(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
            )
        }
        if (notifOff) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "⚠️ Notifications are OFF",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Download progress shows in the notification. Turn it on or downloads will feel ‘stuck’.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            try {
                                val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                context.startActivity(i)
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Turn on notifications") }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // History / About / Update engine → moved to the bottom utility area (branded layout)

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Paste a link") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))

        GradientButton(text = "Download") { openDownloadPopup() }

        Spacer(Modifier.height(26.dp))

        // ---- Recent ----
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "RECENT",
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { context.startActivity(Intent(context, HistoryActivity::class.java)) }) {
                Text("View all →", fontSize = 12.sp)
            }
        }
        val frameLoader = remember {
            ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build()
        }
        if (recent.isEmpty()) {
            Text(
                "No downloads yet — paste a link above.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            recent.forEach { r ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            // Tap = seedha PLAY (History nahi)
                            try {
                                context.startActivity(
                                    Intent(context, PlayerActivity::class.java)
                                        .setData(Uri.parse(r.location))
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "Can't play (file deleted?)", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (r.isAudio) {
                            Text("🎵", fontSize = 17.sp)
                        } else {
                            // Asli video ka frame-thumbnail (saved file se)
                            AsyncImage(
                                model = r.location,
                                imageLoader = frameLoader,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(r.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${r.platform} · ${if (r.isAudio) "Audio" else "Video"}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---- Connect — home pe sirf Insta + TikTok logos (baaki Settings → Connected accounts) ----
        Text(
            "Connect for private videos",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { startLogin("instagram", "Instagram") }, modifier = Modifier.weight(1f)) {
                Icon(
                    painterResource(R.drawable.ic_logo_instagram),
                    contentDescription = "Instagram",
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                if (ckSites.contains("Instagram")) Text("  ✓", fontSize = 13.sp)
            }
            OutlinedButton(onClick = { startLogin("tiktok", "TikTok") }, modifier = Modifier.weight(1f)) {
                Icon(
                    painterResource(R.drawable.ic_logo_tiktok),
                    contentDescription = "TikTok",
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                if (ckSites.contains("TikTok")) Text("  ✓", fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        Spacer(Modifier.height(28.dp))
    }

}

/** Recent-row ke liye platform emoji. */
private fun platformEmoji(platform: String, isAudio: Boolean): String {
    val p = platform.lowercase()
    return when {
        isAudio -> "🎵"
        "youtube" in p -> "▶️"
        "instagram" in p -> "📸"
        "tiktok" in p -> "🎬"
        "facebook" in p -> "📘"
        else -> "🎞"
    }
}
