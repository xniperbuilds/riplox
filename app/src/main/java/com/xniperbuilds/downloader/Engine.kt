package com.xniperbuilds.downloader

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The yt-dlp engine: one place that updates it, and one place that remembers what happened.
 *
 * Riplox downloads from 1000+ sites and every one of them goes through yt-dlp, so a stale
 * engine is not a degraded app — it is a dead one. YouTube in particular breaks the extractor
 * every few weeks, and the fix always lands upstream days before the user's copy hears about it.
 *
 * The holes this closes, all of them real and all of them measured in this codebase:
 *  1. The daily check ran in MainActivity only (`MainActivity.kt`, before this file existed).
 *     Anyone who only ever shares from another app's share sheet never opened MainActivity and
 *     therefore never updated the engine at all.
 *  2. The result was swallowed by `catch (_: Exception) {}`. An update that failed every single
 *     time looked exactly like one that succeeded — including in About, which reported "✓ Engine
 *     updated" off a call whose outcome it never inspected.
 *  3. There was no self-heal anywhere. A download that failed because the extractor had gone out
 *     of date failed permanently, and the user's only clue was "Unable to extract".
 *
 * ⚠️ NIGHTLY, not STABLE. The stable channel routinely runs weeks behind the extractor fixes,
 * which on YouTube is the difference between working and "No video formats found".
 */
object Engine {

    private const val TAG = "XniperDL"

    /** The outcome of an update attempt, in words a non-technical user can act on. */
    enum class Outcome { UPDATED, ALREADY_LATEST, FAILED }

    private fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    /** The engine version string, or null when it cannot be read. */
    fun version(context: Context): String? = try {
        YoutubeDL.getInstance().version(context)
    } catch (e: Exception) {
        Log.w(TAG, "engine version unavailable: ${e.message}")
        null
    }

    /**
     * Update the engine and RECORD the result.
     *
     * The day marker is written only on success, so a failed attempt is retried at the next
     * opportunity instead of being treated as "done for today".
     */
    suspend fun update(context: Context): Outcome = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val outcome = try {
            val status = YoutubeDL.getInstance()
                .updateYoutubeDL(app, YoutubeDL.UpdateChannel.NIGHTLY)
            // Matched on the NAME rather than the enum constant: the library has renamed these
            // before, and an update that works must not be reported as a failure because a
            // constant moved.
            if (status?.name?.contains("ALREADY", ignoreCase = true) == true) {
                Outcome.ALREADY_LATEST
            } else {
                Outcome.UPDATED
            }
        } catch (e: Exception) {
            Log.w(TAG, "engine update failed: ${e.message}")
            Outcome.FAILED
        }
        Prefs.setEngineResult(app, outcome.name, version(app).orEmpty())
        if (outcome != Outcome.FAILED) Prefs.setLastUpdateDay(app, today())
        Log.i(TAG, "engine update → $outcome (${Prefs.engineVersion(app)})")
        outcome
    }

    /** Is a check due today, and is this a safe moment to run one? */
    fun dueNow(context: Context): Boolean {
        if (Prefs.lastUpdateDay(context) == today()) return false
        // Wi-Fi-only ON + mobile data → not now. The engine is ~10 MB; that is the user's data.
        if (Prefs.wifiOnly(context) && isMetered(context)) return false
        return true
    }

    /**
     * The once-a-day check.
     *
     * ⚠️ NEVER call this from a share-sheet activity. That path enqueues a download and then
     * hands off, so an update fired there swaps the yt-dlp binary out from under a job that is
     * starting — the same class of race that deleted a running download's temp folder in
     * 2026-07-16. The safe call sites are: the home screen (no download in flight) and the
     * worker itself, which is serialised with its own download.
     */
    suspend fun dailyIfDue(context: Context) {
        if (!dueNow(context)) return
        val busy = withContext(Dispatchers.IO) { DownloadQueue.hasActive(context) }
        if (busy) return
        update(context)
    }

    /**
     * Is this failure the kind a fresh engine could fix?
     *
     * Deliberately broad: anything about extraction, formats or an out-of-date engine counts.
     * A missed self-heal costs the user a failed download; an unnecessary one costs a few
     * seconds on an attempt that was already going to be retried. The asymmetry decides it.
     */
    fun looksStale(raw: String?): Boolean {
        val s = raw?.lowercase() ?: return false
        return listOf(
            "no video formats", "unable to extract", "not available", "requested format",
            "no formats", "confirm you are on the latest", "unsupported url", "extractor",
            "failed to parse", "unable to download webpage", "out of date", "update",
            "file not found", "sign in to confirm"
        ).any { it in s }
    }

    /** A line About / Settings can show as-is. */
    fun statusLine(context: Context): String {
        val v = Prefs.engineVersion(context).ifBlank { version(context).orEmpty() }
        val day = Prefs.lastUpdateDay(context)
        val when_ = if (day.length == 8) {
            "${day.substring(6, 8)}/${day.substring(4, 6)}/${day.substring(0, 4)}"
        } else "never"
        val outcome = when (Prefs.engineOutcome(context)) {
            Outcome.UPDATED.name -> "updated"
            Outcome.ALREADY_LATEST.name -> "already latest"
            Outcome.FAILED.name -> "last check failed"
            else -> "not checked yet"
        }
        return if (v.isBlank()) "Engine: unknown — $outcome" else "Engine $v — $outcome, $when_"
    }
}
