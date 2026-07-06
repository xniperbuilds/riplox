package com.xniperbuilds.downloader

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * "⚡ Instant Download" share tile — ZERO popup, zero friction.
 *
 * AIRLOCK pattern (double-door):
 *  Door 1: ye invisible activity khulti hai → app FOREGROUND me (system download rok nahi sakta)
 *  Chamber: link queue me → intezar sirf itna ke worker apni foreground-notification ke
 *           sath LOCK ho jaye (RUNNING) — uske baad wo khud protected hai
 *  Door 2: start CONFIRM hote hi activity band. Net na ho to 4s me band + "queued" message
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
            Toast.makeText(
                this,
                if (started) "⬇ Download started — progress in notification"
                else "⬇ Queued — starts as soon as network/queue allows",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }
}
