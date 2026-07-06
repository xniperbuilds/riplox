package com.xniperbuilds.downloader

import android.os.Bundle
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xniperbuilds.downloader.ui.theme.XniperDownloaderTheme

/** 💡 Tips — best use + (expand) full feature guide. */
class TipsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XniperDownloaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { p ->
                    TipsScreen(Modifier.padding(p))
                }
            }
        }
    }
}

private data class Tip(val emoji: String, val title: String, val body: String)

private val TIPS = listOf(
    Tip("⬇", "Fastest way", "Share any video to Riplox → ⚡ Instant. No app-opening, no taps."),
    Tip("🔗", "Connect your accounts", "Instagram & many sites now block guests — sign in on the real site once and private / age-restricted / blocked videos start working. ✓ shows only after a REAL login."),
    Tip("🎛", "Pick quality per download", "The Download popup remembers your choices as the new defaults."),
    Tip("📊", "Watch your queue", "Home → ⬇ icon: running %, queued, failed (with Retry) and completed — tap any completed item to play."),
    Tip("🔒", "Secret vault", "Tap the Riplox logo on home — fingerprint/PIN locked. Vault files vanish from gallery, history and backups. History → ⋮ → Move to vault."),
    Tip("🔔", "Keep notifications ON", "Progress lives there — and tapping a finished one plays the file."),
    Tip("🔋", "Allow background", "Accept the battery prompt so downloads survive closing the app."),
    Tip("💾", "Moving phones?", "Settings → Backup = one file with settings, history, logins (vault stays private, not included).")
)

private val GUIDE = listOf(
    "Home" to listOf(
        Tip("🔗", "Paste a link", "Paste any video/audio link and hit Download — the popup opens where you pick video or audio, quality or exact format."),
        Tip("🕘", "Recent", "Last downloads with real thumbnails — tap to PLAY, View all for full history."),
        Tip("📸", "Connect buttons", "One-tap login for Instagram / TikTok (✓ = really logged in). More sites in Settings → Connected accounts."),
        Tip("🏷", "The logo is a button", "Tap the Riplox logo → Secret Vault (if enabled in Privacy).")
    ),
    "Share menu (from any app)" to listOf(
        Tip("⚡", "Instant Download", "Zero UI — uses your saved defaults and queues in the background."),
        Tip("🎛", "Download Options", "Same popup as home — choose for THIS download; choices become the new defaults.")
    ),
    "Downloads page (⬇ icon)" to listOf(
        Tip("▶", "Running", "Live progress + Cancel."),
        Tip("↻", "Failed", "Retry any failed download with one tap, or Clear the list."),
        Tip("✓", "Completed", "Full list — tap any item to play."),
        Tip("🔔", "Notification", "Live progress, Cancel, thumbnail — tap a finished notification to play, a failed one to open this page.")
    ),
    "Player" to listOf(
        Tip("⛶", "Controls", "Fullscreen, speed (1× → 2×), seek."),
        Tip("↗", "Open with…", "Send the video/audio to gallery or any other player app.")
    ),
    "Secret Vault" to listOf(
        Tip("🔒", "Hidden & locked", "Home-logo tap → fingerprint/phone-PIN. Files leave gallery, history, Downloads AND backups — they exist only inside the vault."),
        Tip("↩", "Get things back", "Vault item ⋮ → Restore to gallery, or Delete forever."),
        Tip("⚙", "On/off", "Settings → Privacy → Secret vault.")
    ),
    "Settings" to listOf(
        Tip("🎞", "Format", "Audio format/quality (MP3, M4A…), container (MP4/MKV), codec, embedded thumbnail & chapters."),
        Tip("🌐", "Network", "Wi-Fi only, speed limit, multi-thread, proxy."),
        Tip("📁", "Files", "Custom folder / SD card, filename template, skip-duplicates, storage usage."),
        Tip("🔗", "Connected accounts", "Site logins + Saved cookies page (disconnected accounts' cookies live there until you delete them)."),
        Tip("🔒", "Privacy", "Incognito history + Secret vault toggle."),
        Tip("💾", "Backup / Restore", "One file with everything (vault excluded — stays private)."),
        Tip("⚡", "Advanced", "Trash auto-purge, playlist range, custom yt-dlp flags, SponsorBlock.")
    ),
    "History & Trash" to listOf(
        Tip("📜", "History", "Tap = play. Filter, search, Select mode (bulk delete / copy links), ⋮ → re-download, Move to vault."),
        Tip("🗑", "Trash", "Deleted items wait here (auto-purge configurable) — restore or delete forever.")
    )
)

@Composable
private fun TipsScreen(modifier: Modifier = Modifier) {
    var showGuide by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        RiploxHeader(subtitle = "Tips", tileSize = 36)
        Spacer(Modifier.height(16.dp))

        TIPS.forEach { t -> TipCard(t) }

        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = { showGuide = !showGuide },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (showGuide) "Hide full guide" else "📖 Full feature guide") }

        if (showGuide) {
            GUIDE.forEach { (section, items) ->
                Spacer(Modifier.height(16.dp))
                Text(
                    section.uppercase(),
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                items.forEach { t -> TipCard(t) }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun TipCard(t: Tip) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(14.dp)) {
            Text(t.emoji, fontSize = 18.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(t.title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    t.body,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
