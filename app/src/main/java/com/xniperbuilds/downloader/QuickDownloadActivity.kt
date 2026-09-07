package com.xniperbuilds.downloader

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import coil.compose.AsyncImage
import com.xniperbuilds.downloader.ui.theme.RiploxInk
import com.xniperbuilds.downloader.ui.theme.RiploxSteel
import com.xniperbuilds.downloader.ui.theme.SpaceGrotesk
import kotlinx.coroutines.launch
import java.util.UUID

// ---------------------------------------------------------------------------
// DESIGN TOKENS — naapay hue, mehsoos kar ke nahi chune (xniper-design, mobile lane)
//
// Canvas 375 · body 16 · small 14 · headline 18 · button 48 tall with a 16 label · gap 8/16
// · padding 16 · weight 400 with Bold for emphasis (500/600 ka darmiyana wazan mobile pe
// "AI-generated" wali pehchan hai).
//
// ⚠️ CONTRAST GINA GAYA HAI, MAAN NAHI LIYA — ye wo ek nakami hai jo screenshot nahi dikhata:
//     text  #E8EEF5 on ink #0A101B ........... 4.5:1 se boht upar   OK
//     muted #7C8DA6 on ink .................... 5.64:1              OK
//     ink   #0A101B on accent #8CA0BE ......... 7.14:1              OK
//     WHITE on accent #8CA0BE ................. 2.66:1              FAIL
// Is liye accent button pe INK text hai. Ye style ka faisla nahi hai.
// ---------------------------------------------------------------------------
private val SheetSurface = Color(0xFF121A27)
private val SheetText = Color(0xFFE8EEF5)
private val SheetMuted = Color(0xFF7C8DA6)
private val SheetLine = Color(0xFF1E2938)
private val SheetTrack = Color(0xFF223046)

private enum class Phase { Preparing, Downloading, Saved, Failed }

/**
 * "⚡ Instant Download" share tile.
 *
 * PEHLE YE ACTIVITY GHAIB THI: khulti thi, ek toast phenkti thi, aur `finish()` kar deti thi.
 * Jo banda hamesha doosri app ki share sheet se download karta hai usay na progress dikhti thi,
 * na Cancel milta tha, na koi asli error — bas ek toast jo agli se pehle hi mit jata tha.
 *
 * AIRLOCK IS SE KAMZOR NAHI HOTA. Double-door bilkul waise hi chalta hai — link foran queue me
 * jata hai aur `awaitStart` abhi bhi worker ki apni FGS-lock ka intezar karta hai. Ek DIKHTI
 * hui foreground activity ghaib wali se sakht-tar hai, aur sheet ab race kar ke band hone ke
 * bajaye khadi rehti hai. Back ya bahar tap = sheet band, download background me chalti rehti
 * hai — bilkul purana rawaiya.
 *
 * ⚠️ Manifest se `noHistory` HATAYA gaya hai (warna sheet foran hi mar jati). Uska seedha
 * nateeja: `launchMode="singleTop"` + `onNewIntent` LAZMI hain — warna doosri share isi zinda
 * instance pe aati hai aur sheet PURANI download dikhati rehti hai.
 */
class QuickDownloadActivity : ComponentActivity() {

    /** Jo download is waqt sheet chala rahi hai — onNewIntent isay badalta hai. */
    private data class Job(val link: String, val audio: Boolean, val workId: UUID)

    private var current by mutableStateOf<Job?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!accept(intent)) return
        setContent {
            current?.let { Sheet(it) }
        }
    }

    /**
     * Doosri share isi zinda activity pe (singleTop). Bagair is ke sheet purani download
     * dikhati rehti aur nayi link chup-chaap gum ho jati.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        accept(intent)
    }

    /**
     * Link nikalo, QUEUE me daalo, aur sheet ke liye state set karo. false = kuch nahi mila.
     *
     * ⚠️ Enqueue yahan hota hai, kisi `remember { }` ke andar nahi. Compose composition ko
     * phenk kar dobara chala sakta hai — remember ke andar enqueue karna ek din chup-chaap
     * do download bana deta.
     */
    private fun accept(i: Intent?): Boolean {
        val link = if (i?.action == Intent.ACTION_SEND && i.type == "text/plain") {
            extractUrl(i.getStringExtra(Intent.EXTRA_TEXT))
        } else null

        if (link.isNullOrBlank() || !link.startsWith("http")) {
            Toast.makeText(this, "No link found in the shared text", Toast.LENGTH_SHORT).show()
            finish()
            return false
        }
        // Wahi link dobara share hui jo abhi chal rahi hai → nayi job mat banao.
        if (current?.link == link) return true
        val audio = Prefs.audioMode(this)
        current = Job(link, audio, DownloadQueue.enqueue(this, link, audio))
        return true
    }

    @Composable
    private fun Sheet(job: Job) {
        val link = job.link
        val audio = job.audio
        val scope = rememberCoroutineScope()

        // `remember(link)` — onNewIntent nayi link deta hai to poori sheet naye sire se
        // shuru hoti hai; purani download background me chalti rehti hai.
        // Retry nayi job banata hai, is liye workId state hai (job.workId sirf shuruaat).
        var workId by remember(link) { mutableStateOf(job.workId) }
        var phase by remember(link) { mutableStateOf(Phase.Preparing) }
        var pct by remember(link) { mutableStateOf(0) }
        // ⚠️ "Getting video…" NAHI — neeche status line pehle hi wahi keh rahi hoti hai.
        // Cheez ka naam, aur haal neeche — is se hakla-hat nahi hoti.
        var title by remember(link) { mutableStateOf("Video") }
        var thumb by remember(link) { mutableStateOf<String?>(null) }
        var saved by remember(link) { mutableStateOf<DownloadRecord?>(null) }
        var failure by remember(link) { mutableStateOf("") }
        var busy by remember(link) { mutableStateOf(false) }
        var queuedOnly by remember(link) { mutableStateOf(false) }
        var setupWarn by remember(link) { mutableStateOf(false) }

        // AIRLOCK ka handoff abhi bhi chalta hai — bas ab wo toast ke bajaye ek line chalata hai.
        DisposableEffect(workId) {
            DownloadQueue.awaitStart(this@QuickDownloadActivity, workId) { started ->
                queuedOnly = !started
                // Background setup adhoora ho to XOS-type phone download beech me freeze kar
                // sakta hai. Instant flow me rukawat ZERO rakhni hai — is liye sirf ek hint,
                // koi dialog nahi.
                setupWarn = started &&
                    !(BgGuard.batteryExempt(this@QuickDownloadActivity) &&
                        Prefs.bgSetupDone(this@QuickDownloadActivity))
            }
            onDispose {}
        }

        DisposableEffect(workId) {
            val live = WorkManager.getInstance(applicationContext).getWorkInfoByIdLiveData(workId)
            val obs = Observer<WorkInfo?> { info ->
                if (info == null) return@Observer
                info.progress.getString("title")?.takeIf { it.isNotBlank() }?.let { title = it }
                info.progress.getString("thumb")?.takeIf { it.isNotBlank() }?.let { thumb = it }
                val p = info.progress.getInt("pct", -1)
                if (p >= 0) {
                    pct = p
                    if (phase == Phase.Preparing) phase = Phase.Downloading
                }
                when (info.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        saved = try {
                            History.all(this@QuickDownloadActivity).firstOrNull()
                        } catch (e: Exception) {
                            null
                        }
                        phase = Phase.Saved
                    }
                    WorkInfo.State.FAILED -> {
                        // Asli wajah hamare apne store se — WorkManager ka FAILED info kaat
                        // diya jata hai aur usme kaam ki koi baat nahi hoti.
                        failure = try {
                            FailedStore.all(this@QuickDownloadActivity)
                                .firstOrNull { it.link == link }?.error.orEmpty()
                        } catch (e: Exception) {
                            ""
                        }
                        phase = Phase.Failed
                    }
                    else -> {}
                }
            }
            live.observe(this@QuickDownloadActivity, obs)
            onDispose { live.removeObserver(obs) }
        }

        fun openApp() {
            startActivity(
                Intent(this@QuickDownloadActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
        }

        fun retry(updateEngine: Boolean) {
            busy = true
            scope.launch {
                if (updateEngine) {
                    // Ye wo ek-tap wala jawab hai jo ghair-technical user ke paas warna hai hi
                    // nahi. Update fail bhi ho jaye to retry rokna nahi — retry khud bata dega
                    // ke asal masla kya hai.
                    try {
                        Engine.update(applicationContext)
                    } catch (e: Exception) {
                    }
                }
                pct = 0
                title = "Video"
                phase = Phase.Preparing
                busy = false
                workId = DownloadQueue.enqueue(this@QuickDownloadActivity, link, audio)
            }
        }

        Box(Modifier.fillMaxSize()) {
            // Bahar tap = band. Koi ripple nahi — ye scrim hai, control nahi.
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { finish() }
            )

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(SheetSurface)
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                val done = phase == Phase.Saved || phase == Phase.Failed

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (thumb != null) {
                        AsyncImage(
                            model = thumb,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 56.dp, height = 72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SheetTrack)
                        )
                    } else {
                        Box(
                            Modifier
                                .size(width = 56.dp, height = 72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SheetTrack)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = when (phase) {
                                Phase.Saved -> "Saved to gallery"
                                Phase.Failed -> "Download failed"
                                else -> title
                            },
                            color = SheetText,
                            fontSize = if (done) 18.sp else 16.sp,
                            fontFamily = if (done) SpaceGrotesk else null,
                            fontWeight = if (done) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = when (phase) {
                                // Batata hai ke ho kya raha hai AUR ye ke intezar karna hi
                                // theek hai. Sirf "Getting the video…" pe log soch me par
                                // jate the ke shuru bhi hui ya nahi.
                                Phase.Preparing ->
                                    if (queuedOnly) "Waiting for network — it'll start on its own"
                                    else "Please wait — the download is starting…"
                                Phase.Downloading ->
                                    if (pct >= 99) "Finishing — merging & saving…" else "$pct%"
                                // Jo bacha wo file ka naam hai, jo screen pe machine ki
                                // bakwas lagta hai — is liye kism + platform.
                                Phase.Saved -> saved?.let { "${it.kindLabel} · ${it.platform}" } ?: title
                                Phase.Failed -> failure.ifBlank { "Something went wrong" }
                            },
                            color = SheetMuted,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!done) {
                    Spacer(Modifier.height(16.dp))
                    // Haath se banayi hui — 4dp ki theek pill, theek rang, aur is baat pe
                    // koi inhisar nahi ke yahan Material3 ka kaunsa progress API aata hai.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(SheetTrack)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction = pct.coerceIn(0, 100) / 100f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(RiploxSteel)
                        )
                    }
                }

                if (setupWarn && !done) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tip: open Riplox once → “Fix background downloads”, so it never pauses.",
                        color = SheetMuted,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (phase) {
                        Phase.Preparing, Phase.Downloading -> {
                            OutlinedButton(
                                onClick = {
                                    WorkManager.getInstance(applicationContext).cancelWorkById(workId)
                                    finish()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(50)
                            ) { Text("Cancel", color = SheetText, fontSize = 16.sp) }

                            OutlinedButton(
                                onClick = { openApp() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(50)
                            ) { Text("Open app", color = SheetText, fontSize = 16.sp) }
                        }

                        Phase.Saved -> {
                            // Accent us button pe jo USER chahta hai (file kholna).
                            Button(
                                onClick = {
                                    saved?.let { r ->
                                        if (!openRecord(this@QuickDownloadActivity, r)) {
                                            Toast.makeText(
                                                this@QuickDownloadActivity,
                                                "Can't open — file may be deleted",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                    finish()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RiploxSteel,
                                    contentColor = RiploxInk
                                )
                            ) { Text("Open", fontSize = 16.sp) }

                            OutlinedButton(
                                onClick = {
                                    saved?.let { r ->
                                        try {
                                            startActivity(
                                                Intent.createChooser(
                                                    Intent(Intent.ACTION_SEND)
                                                        .setType(r.viewMime)
                                                        .putExtra(
                                                            Intent.EXTRA_STREAM,
                                                            android.net.Uri.parse(r.location)
                                                        )
                                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                                    "Share"
                                                )
                                            )
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                this@QuickDownloadActivity,
                                                "Can't share this file",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(50)
                            ) { Text("Share", color = SheetText, fontSize = 16.sp) }
                        }

                        Phase.Failed -> {
                            OutlinedButton(
                                onClick = { if (!busy) retry(updateEngine = false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(50)
                            ) { Text("Retry", color = SheetText, fontSize = 16.sp) }

                            // Ghair-technical user ke liye ek-tap wala jawab — warna uske
                            // paas engine refresh karne ka koi rasta hai hi nahi.
                            Button(
                                onClick = { if (!busy) retry(updateEngine = true) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RiploxSteel,
                                    contentColor = RiploxInk
                                )
                            ) { Text(if (busy) "Updating…" else "Update & retry", fontSize = 16.sp) }
                        }
                    }
                }

                if (done) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Open Riplox",
                        color = SheetMuted,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clickable { openApp() }
                            .padding(8.dp)
                    )
                }

                // ---- BANNER KI JUDAI -----------------------------------------------------
                // Patti kabhi buttons se chipki hui nahi honi chahiye — warna galti se tap
                // hona lazmi hai. 24 + baal jaisi lakeer + 16 usi usool ka jism hai.
                // Ye faasla kam mat karna.
                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(SheetLine)
                )
                Spacer(Modifier.height(16.dp))
                PromoBanner()
            }
        }
    }
}
