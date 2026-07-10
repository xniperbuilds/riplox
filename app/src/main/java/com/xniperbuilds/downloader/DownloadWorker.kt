package com.xniperbuilds.downloader

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import coil.ImageLoader
import coil.request.ImageRequest
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * WorkManager-based download — background mode ka NAYA engine (Phase 3).
 * Faayde: app band / phone reboot pe bhi chale, auto-retry (3x + backoff), proper queue
 * (har enqueue guaranteed chale), foreground notification + progress + Cancel.
 * PLAN ke 4 bugs ka fix (slow/retry/app-band/queue) yahin.
 */
class DownloadWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    private val progId get() = 4000 + ((id.hashCode() and 0x7FFF) shl 1) // live progress notif

    // Stall-watchdog: yt-dlp ke HAR output pe reset hota. Itni der tak koi output
    // nahi = process network pe HANG hai (na fail na aage) → kill → retry.
    // Iske bagair ek hangi download apna slot HAMESHA pakde rehti thi — naye
    // downloads "start hi nahi" hote the aur sirf Clear-data se theek hota tha.
    @Volatile private var lastBeat = 0L

    private companion object {
        const val STALL_MS = 5 * 60 * 1000L   // 5 min no-output = stalled
        const val WATCH_EVERY_MS = 30_000L    // check interval
    }

    /** Expedited work (Android 12 se neeche) ke liye WM isay khud call karta hai. */
    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(progId, "⬇ Downloading…", "starting…", null)

    override suspend fun doWork(): Result {
        val link = inputData.getString("link") ?: return Result.failure()
        val audio = inputData.getBoolean("audio", false)
        // Per-download overrides ("Download Options" tile) — absent = global Settings
        val fmt = inputData.getString("format")
        val qual = inputData.getString("quality")
        val pl = when (inputData.getInt("playlist", -1)) { 1 -> true; 0 -> false; else -> null }
        val sub = when (inputData.getInt("subs", -1)) { 1 -> true; 0 -> false; else -> null }
        val afmt = inputData.getString("audioFormat")
        val nm = NotificationManagerCompat.from(ctx)
        val doneId = progId + 1 // final (success/fail) notif
        val pid = "wk_$id" // yt-dlp process id — Cancel pe isi se process kill hota hai

        // Title/thumbnail PARALLEL me aate hain — pehle ye download se PEHLE serial the
        // (har download +15-40s slow + notif late). Ab notif turant, download turant.
        var title = "Downloading…"
        var bmp: Bitmap? = null

        // FOREGROUND LOCK — door (share-activity/app) khula ho to FGS foran lag jati hai →
        // download system ke quota/defer/XOS killer se protected. Ye lag jaye us ke BAAD hi
        // door band hota hai (awaitStart "fg" signal). Background retry pe Android 12+ FGS
        // start block kar de to bhi download NAHI rokni — job apni window me bina lock ke
        // chale (progress notifs nm.notify se waise bhi aati hain).
        val fgLocked = try {
            setForeground(foregroundInfo(progId, "⬇ Downloading…", "starting…", null))
            true
        } catch (e: Exception) {
            Log.w("XniperDL", "FGS lock denied (bg start?) — running unlocked", e)
            false
        }
        return try {
            setProgressAsync(workDataOf("fg" to fgLocked))
            coroutineScope {
                val pvJob = launch(Dispatchers.IO) {
                    val pv = try { getPreview(ctx, link) } catch (e: Exception) { null }
                    if (pv != null) {
                        title = pv.title
                        bmp = pv.thumbnail?.let { loadThumb(it) }
                        safeNotify(nm, progId, buildNotif("⬇ $title", "downloading…", true, bmp))
                        // "fg" HAR progress-update me — WM progress poora REPLACE hota hai,
                        // key chhoot jaye to door ka lock-signal ud jata
                        setProgressAsync(androidx.work.workDataOf("title" to title, "fg" to fgLocked))
                    }
                }
                try {
                    val where = DownloadGate.withSlot(Prefs.simultaneous(ctx)) {
                        // Cancel = sirf coroutine nahi, yt-dlp PROCESS bhi maro — warna download
                        // chupke chalta rehta (data/battery burn) aur save bhi ho jata.
                        val killer = launch {
                            try {
                                awaitCancellation()
                            } finally {
                                withContext(NonCancellable + Dispatchers.IO) {
                                    try { YoutubeDL.getInstance().destroyProcessById(pid) } catch (_: Throwable) {}
                                }
                            }
                        }
                        // Watchdog — STALL_MS tak koi output nahi to process kill (→ retry).
                        // Slot kabhi permanently jam nahi hota (kal wala "clear data" bug).
                        lastBeat = System.currentTimeMillis()
                        val watchdog = launch(Dispatchers.IO) {
                            while (true) {
                                kotlinx.coroutines.delay(WATCH_EVERY_MS)
                                if (System.currentTimeMillis() - lastBeat > STALL_MS) {
                                    Log.w("XniperDL", "watchdog: no output ${STALL_MS / 1000}s — killing $pid")
                                    try { YoutubeDL.getInstance().destroyProcessById(pid) } catch (_: Throwable) {}
                                    break
                                }
                            }
                        }
                        try {
                            withContext(Dispatchers.IO) {
                                runDownload(
                                    ctx, link, audio, fmt, pid, qual, pl, sub, afmt,
                                    onBeat = { lastBeat = System.currentTimeMillis() }
                                ) { p ->
                                    safeNotify(nm, progId, buildNotif("⬇ $title", "$p%", true, bmp))
                                    setProgressAsync(androidx.work.workDataOf("pct" to p, "title" to title, "fg" to fgLocked))
                                }
                            }
                        } finally {
                            watchdog.cancel()
                            killer.cancel()
                        }
                    }
                    // Done-notif: thumbnail bada + ▶ Play action (abhi save hui file kholta)
                    val playLoc = withContext(Dispatchers.IO) {
                        try { History.all(ctx).firstOrNull()?.location } catch (e: Exception) { null }
                    }
                    safeNotify(nm, doneId, buildNotif("✓ $title", where, false, bmp, bigPicture = true, playUri = playLoc))
                } finally {
                    pvJob.cancel()
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e // user ne Cancel dabaya — retry/fail-notif nahi
        } catch (e: Exception) {
            Log.e("XniperDL", "worker fail (attempt $runAttemptCount)", e)
            // Retry count user-settable (Settings → Downloads, default 3 total attempts)
            if (runAttemptCount < Prefs.maxRetries(ctx) - 1) {
                Result.retry()
            } else {
                val msg = smartError(ctx, link, e.message)
                // FailedStore = Downloads page ka Failed section + Retry data (reliable, WM se azaad)
                try { FailedStore.add(ctx, link, title, audio, msg) } catch (_: Exception) {}
                safeNotify(
                    nm, doneId,
                    buildNotif("❌ Download failed", msg, false, null, openDownloads = true)
                )
                Result.failure()
            }
        }
    }

    private suspend fun loadThumb(url: String): Bitmap? = try {
        val loader = ImageLoader(ctx)
        val req = ImageRequest.Builder(ctx).data(url).allowHardware(false).build()
        (loader.execute(req).drawable as? BitmapDrawable)?.bitmap
    } catch (e: Exception) {
        null
    }

    private fun buildNotif(
        title: String,
        text: String,
        ongoing: Boolean,
        largeIcon: Bitmap?,
        bigPicture: Boolean = false,
        playUri: String? = null,
        openDownloads: Boolean = false
    ): Notification {
        val icon = if (ongoing) android.R.drawable.stat_sys_download
        else android.R.drawable.stat_sys_download_done
        val b = NotificationCompat.Builder(ctx, XniperApp.CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(ongoing)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        // Done notif pe thumbnail bada dikhao (BigPicture); warna text expand.
        if (bigPicture && largeIcon != null) {
            b.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(largeIcon)
                    .bigLargeIcon(null as Bitmap?)
            )
        } else {
            b.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }
        if (largeIcon != null) b.setLargeIcon(largeIcon)
        if (ongoing) {
            b.addAction(0, "Cancel", WorkManager.getInstance(ctx).createCancelPendingIntent(id))
        }
        // Notif tap = HAMESHA kuch khule: done→Play (ya app), fail→Downloads page
        if (!ongoing) {
            try {
                val tapIntent = when {
                    playUri != null -> Intent(ctx, PlayerActivity::class.java)
                        .setData(Uri.parse(playUri))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    openDownloads -> Intent(ctx, DownloadsActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    else -> Intent(ctx, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val pi = PendingIntent.getActivity(
                    ctx, (playUri ?: title).hashCode(),
                    tapIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                b.setContentIntent(pi)
                b.setAutoCancel(true)
                if (playUri != null) b.addAction(0, "▶ Play", pi)
            } catch (_: Exception) {
            }
        }
        return b.build()
    }

    private fun foregroundInfo(notifId: Int, title: String, text: String, bmp: Bitmap?): ForegroundInfo {
        val n = buildNotif(title, text, true, bmp)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notifId, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notifId, n)
        }
    }

    private fun safeNotify(nm: NotificationManagerCompat, notifId: Int, n: Notification) {
        try {
            nm.notify(notifId, n)
        } catch (e: SecurityException) {
        }
    }
}

/** Background download queue — har download ek WorkManager task (guaranteed + retry + persistent). */
object DownloadQueue {
    const val TAG = "dl"

    fun enqueue(
        context: Context,
        link: String,
        audio: Boolean,
        quality: String? = null,     // per-download overrides (Download Options tile)
        playlist: Boolean? = null,
        subs: Boolean? = null,
        format: String? = null,
        audioFormat: String? = null
    ): java.util.UUID {
        // Network constraint: net na ho to attempts burn nahi hote — WM khud net aane ka wait karta.
        // Wi-Fi-only ON → UNMETERED (mobile data pe start hi nahi hota).
        val net = if (Prefs.wifiOnly(context)) NetworkType.UNMETERED else NetworkType.CONNECTED
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    "link" to link,
                    "audio" to audio,
                    "quality" to quality,
                    "playlist" to (playlist?.let { if (it) 1 else 0 } ?: -1),
                    "subs" to (subs?.let { if (it) 1 else 0 } ?: -1),
                    "format" to format,
                    "audioFormat" to audioFormat
                )
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(net).build())
            // ⚠️ EXPEDITED YAHAN DOBARA MAT LAGANA (2026-07-09 lesson): Android 12+ pe
            // expedited = QUOTA-job (FGS nahi) — quota sirf app-open pe refill hota, is liye
            // (a) start app-open tak atakta tha, (b) app band karte hi chalti download beech
            // me STOP ho jati thi (40% stuck). Turant-start ab AIRLOCK deta hai: door (activity)
            // foreground me hai → job foran RUNNING → setForeground() se REAL dataSync FGS lock
            // → quota/defer/app-band sab se azaad.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(req)
        return req.id
    }

    /**
     * "AIRLOCK" v2 — share-tile activity ko tab tak zinda rakho jab tak download worker
     * apni FOREGROUND-SERVICE LOCK ke sath sach me protected na ho jaye.
     * v1 ka bug: door sirf RUNNING pe band ho jata tha — RUNNING ≠ lock. Activity band
     * hote hi app background me, aur worker ki setForeground() Android 12+ pe background
     * se BLOCK ho sakti thi → download unprotected reh jati (app band = stuck).
     * v2: worker lock lagne ke baad progress me "fg"=true bhejta hai — door SIRF us
     * confirm pe band hota. Timeout = net na ho/queue full to bhi atko mat (job WM me
     * safe enqueued hai, net/mauqa milte hi chalegi).
     */
    fun awaitStart(
        activity: androidx.activity.ComponentActivity,
        id: java.util.UUID,
        timeoutMs: Long = 10_000,
        onDone: (started: Boolean) -> Unit
    ) {
        var fired = false
        fun fire(started: Boolean) {
            if (!fired) {
                fired = true
                onDone(started)
            }
        }
        try {
            WorkManager.getInstance(activity.applicationContext)
                .getWorkInfoByIdLiveData(id)
                .observe(activity) { info ->
                    if (info == null) return@observe
                    // fg=true → FGS lock confirm; isFinished → itni tez khatam/fail ke
                    // intezar ka matlab nahi. Sirf RUNNING pe ab door band NAHI hota.
                    if (info.progress.getBoolean("fg", false) || info.state.isFinished) {
                        fire(true)
                    }
                }
        } catch (e: Exception) {
            fire(false)
            return
        }
        android.os.Handler(activity.mainLooper).postDelayed({ fire(false) }, timeoutMs)
    }

    /** Koi background download RUNNING hai? (engine-update / temp-clear guard ke liye) */
    fun hasActive(context: Context): Boolean = try {
        WorkManager.getInstance(context.applicationContext)
            .getWorkInfosByTag(TAG).get()
            .any { it.state == androidx.work.WorkInfo.State.RUNNING }
    } catch (e: Exception) {
        false
    }
}

/**
 * Simultaneous-downloads gate — ek waqt me sirf N background downloads chalein (user slider 1–5).
 * WorkManager/manifest ko chheDe bagair (safe): semaphore se permit lelo, download karo, chhoD do.
 * N change ho to naya semaphore banta; acquire/release usi captured instance pe (safe).
 */
object DownloadGate {
    @Volatile private var permits = -1
    @Volatile private var sem = Semaphore(3)

    @Synchronized private fun semFor(n: Int): Semaphore {
        if (n != permits) {
            permits = n
            sem = Semaphore(n)
        }
        return sem
    }

    suspend fun <T> withSlot(n: Int, block: suspend () -> T): T {
        val s = semFor(n.coerceIn(1, 5))
        s.acquire()
        try {
            return block()
        } finally {
            s.release()
        }
    }
}
