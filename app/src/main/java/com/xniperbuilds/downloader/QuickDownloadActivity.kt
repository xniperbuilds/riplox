package com.xniperbuilds.downloader

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * "⚡ Instant Download" share tile — ZERO popup, zero friction.
 *
 * AIRLOCK pattern (double-door) v2:
 *  Door 1: ye invisible activity khulti hai → app FOREGROUND me (system download rok nahi sakta)
 *  Chamber: link queue me → intezar sirf itna ke worker apni FOREGROUND-SERVICE lock laga le
 *           ("fg" confirm — sirf RUNNING kaafi nahi tha, wohi purana race/stuck bug)
 *  Door 2: lock CONFIRM hote hi activity band. Net na ho to 10s me band + "queued" message
 *          (net aate hi WorkManager khud start kar dega).
 */
class QuickDownloadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val link = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
        } else null

        if (link.isNullOrBlank() || !link.startsWith("http")) {
            Toast.makeText(this, "No link found in the shared text", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val workId = DownloadQueue.enqueue(this, link, Prefs.audioMode(this))
        DownloadQueue.awaitStart(this, workId) { started ->
            // Background-setup adhoora ho to XOS-type phone download beech me freeze kar
            // sakta hai — Instant flow me friction ZERO rakhni hai, is liye sirf hint-toast.
            val setupOk = BgGuard.batteryExempt(this) && Prefs.bgSetupDone(this)
            val msg = when {
                started && setupOk -> "⬇ Download started — progress in notification"
                started -> "⬇ Started — open Riplox once → “Fix background downloads” (so it never pauses)"
                else -> "⬇ Queued — starts as soon as network/queue allows"
            }
            Toast.makeText(this, msg, if (started && !setupOk) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
