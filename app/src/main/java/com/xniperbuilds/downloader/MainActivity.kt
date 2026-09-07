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
import androidx.compose.material3.AlertDialog
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
    // XOS/Hiber-type killers: battery exemption ke bina background/Instant downloads
    // freeze ho jati hain — aur XOS pe exemption KAAFI NAHI (auto-start + recents-lock
    // bhi chahiye). Banner: jab tak user setup "Done" na kare, YA exemption chhin jaye.
    var bgRisk by remember {
        mutableStateOf(!BgGuard.batteryExempt(context) || !Prefs.bgSetupDone(context))
    }
    var showBgSetup by remember { mutableStateOf(false) }

    // App ki apni version — BuildConfig is project me generate nahi hota (AGP 8 me default
    // off hai), is liye wahi raasta jo AboutActivity pehle se istemal karti hai.
    val appVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    // GitHub pe is se nayi version pari hai? (khali = kuch nahi dikhana)
    var newVersion by remember { mutableStateOf(Updates.pendingVersion(context, appVersion)) }

    // Download = hamesha Options-popup (wahan video/audio/quality choose + choices save hoti hain)
    fun openDownloadPopup() {
        // BATCH PASTE — ek se zyada link ek sath. Chat/notes se copy kiya hua text seedha
        // yahan chipka do; har link apni download ban jata hai.
        // ⚠️ Kai link ho to Download-Options popup NAHI khulta: wo popup EK download ke
        // liye hai (quality/playlist/format uske apne). 10 link pe 10 popup lagana rukawat
        // hai, faida nahi — is liye batch seedha global Settings ke sath queue me jata hai.
        val links = extractUrls(url)
        when {
            links.isEmpty() -> {
                Toast.makeText(context, "Paste a link first", Toast.LENGTH_SHORT).show()
                return
            }
            links.size == 1 -> {
                context.startActivity(
                    Intent(context, ConfigureDownloadActivity::class.java).putExtra("link", links[0])
                )
            }
            else -> {
                val audio = Prefs.audioMode(context)
                links.forEach { DownloadQueue.enqueue(context, it, audio) }
                Toast.makeText(
                    context,
                    "${links.size} links queued — progress in Downloads",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        url = ""
    }

    // Din me ek dafa engine auto-update (app open pe, background me chupchaap).
    // Guard: (a) koi download chal rahi ho to skip — binary mid-download replace na ho;
    // (b) Wi-Fi-only ON + mobile data → skip (user ka data na jale).
    // ⚠️ Ab ye Engine ke through hai. Purana code seedha updateYoutubeDL() chalata tha aur
    // uska nateeja `catch (_: Exception) {}` me nigal jata tha — har dafa fail hone wala
    // update bilkul kamyab jaisa lagta tha, aur kahin record bhi nahi hota tha.
    // Doosra check DownloadWorker me hai — share-sheet wale user ke liye (wo yahan aata hi nahi).
    LaunchedEffect(Unit) {
        try {
            Engine.dailyIfDue(context)
        } catch (_: Exception) {
        }
    }

    // Din me ek dafa GitHub se poochho ke nayi app-version to nahi aa gayi.
    LaunchedEffect(Unit) {
        try {
            Updates.checkDaily(context)
            newVersion = Updates.pendingVersion(context, appVersion)
        } catch (_: Exception) {
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
        // Settings se wapsi pe status refresh (exemption mili/chhini to banner update)
        bgRisk = !BgGuard.batteryExempt(context) || !Prefs.bgSetupDone(context)
        // Stale/phansi downloads ko dhakka — app khula hai to escort-FGS allowed hai;
        // ENQUEUED job foran chalegi + worker apna FGS-lock le lega ("app open pe bhi
        // start nahi hota" ka ilaj).
        EscortService.kickIfNeeded(context)
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

        // XOS (Infinix/Tecno) jaise phones background me app FREEZE kar dete hain →
        // Instant-share downloads app khole bina start/complete nahi hotin. Ye banner
        // + 3-step setup usi ka permanent ilaj hai (battery + auto-start + recents-lock).
        if (bgRisk) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "⚠️ Background downloads may pause",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Your phone freezes apps in the background, so shared downloads can stall until you open Riplox. A 1-minute setup fixes it for good.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showBgSetup = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Fix background downloads")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (showBgSetup) {
            BgSetupDialog {
                showBgSetup = false
                bgRisk = !BgGuard.batteryExempt(context) || !Prefs.bgSetupDone(context)
            }
        }

        // NAYI APP-VERSION — Play ka in-app-update yahan chal hi nahi sakta (app Play pe nahi
        // hai), aur engine khud ko update kar leta hai magar app khud ko nahi. Bagair is ke
        // v1.0.0 wala user aaj tak v1.0.0 hi chala raha hota hai.
        // ⚠️ Ye khud kuch install nahi karta — sirf Releases ka safha kholta hai.
        if (newVersion.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "⬆️ Riplox $newVersion is out",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "You're on $appVersion. Tap Download, then open the file to install — your downloads, history and logins stay.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                // ⚠️ Seedha APK, releases ka safha NAHI — wahan ghair-technical
                                // user "Source code (zip)" pe tap kar deta hai. Ye link browser
                                // me foran download shuru kar deta hai.
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(Updates.DOWNLOAD_URL))
                                    )
                                } catch (_: Exception) {
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Download") }
                        OutlinedButton(
                            onClick = {
                                Prefs.setDismissedAppVersion(context, newVersion)
                                newVersion = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Later") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // History / About / Update engine → moved to the bottom utility area (branded layout)

        // ⚠️ singleLine = false: batch paste ka poora point yehi hai — kai link chipkane
        // par user ko dikhna chahiye ke usne kya chipkaya. Ek line me wo sirf ek lambi
        // patti dekhta hai aur samajhta hai ke kuch ghalat ho gaya.
        val pastedCount = remember(url) { extractUrls(url).size }
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(if (pastedCount > 1) "$pastedCount links" else "Paste a link") },
            singleLine = false,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))

        GradientButton(
            text = if (pastedCount > 1) "Download $pastedCount links" else "Download"
        ) { openDownloadPopup() }

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
                            // Tap = seedha kholo (History nahi). Photo → gallery viewer.
                            if (!openRecord(context, r)) {
                                Toast.makeText(context, "Can't open (file deleted?)", Toast.LENGTH_SHORT).show()
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
                        } else if (r.isImage) {
                            // Tasveer ka apna preview — frameLoader video-frame extractor hai,
                            // photo pe wo khali box deta tha.
                            AsyncImage(
                                model = r.location,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
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
                            "${r.platform} · ${r.kindLabel}",
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

        // ---- Banner ----
        // ⚠️ Sabse neeche, scroll ke aakhir me — Download field aur Connect buttons se dur.
        // Patti kabhi kisi tap-target ke pehlu me nahi honi chahiye (galti se tap = us
        // banner ki maut, chahe wo hamara apna ho).
        Spacer(Modifier.height(24.dp))
        PromoBanner()

        Spacer(Modifier.height(28.dp))
    }

}

/** 3-step background-setup dialog — Home banner AUR Settings → Downloads dono se khulta
 * (banner "Done" ke baad chhup jata hai, Settings wala raasta HAMESHA rehta). Battery
 * step ka live ✓ status dialog pe wapsi (ON_RESUME) pe refresh hota hai. */
@Composable
fun BgSetupDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    var battOk by remember { mutableStateOf(BgGuard.batteryExempt(context)) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) battOk = BgGuard.batteryExempt(context)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Background setup (one time)") },
        text = {
            Column {
                Text(
                    "Do these 3 steps so downloads keep running with the app closed:",
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { BgGuard.requestBatteryExempt(context) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (battOk) "1 · Battery ✓ already allowed" else "1 · Allow battery (tap → Allow)") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (!BgGuard.openAutoStart(context)) {
                            Toast.makeText(context, "Couldn't open — enable Auto-start in phone settings", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("2 · Turn ON Auto-start for Riplox") }
                Text(
                    "No Auto-start list on your phone? Then: App info → Battery → Allow Background Usage (ON).",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "3 · Open Recent apps, hold the Riplox card and tap the 🔒 lock — this stops the phone from killing it.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Prefs.setBgSetupDone(context, true)
                onClose()
            }) { Text("Done") }
        }
    )
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
