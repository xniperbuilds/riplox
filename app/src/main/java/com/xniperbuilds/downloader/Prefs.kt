package com.xniperbuilds.downloader

import android.content.Context
import org.json.JSONObject

/** Chhoti settings (SharedPreferences) — quick-share ke liye. */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("xniper_prefs", Context.MODE_PRIVATE)

    /** Backup ke liye — saari prefs JSON me daal do (types ke sath). */
    fun dumpInto(o: JSONObject, c: Context) {
        for ((k, v) in sp(c).all) if (v != null) o.put(k, v)
    }

    /** Restore ke liye — JSON se prefs wapas load karo (type infer karke).
     * Hamari saari numeric prefs Int hain — Long/Double bhi Int me save (warna
     * getInt() pe ClassCastException crash ho sakta tha edited backups pe). */
    fun loadFrom(o: JSONObject, c: Context) {
        val e = sp(c).edit()
        for (k in o.keys()) {
            when (val v = o.get(k)) {
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putInt(k, v.toInt())
                is Double -> e.putInt(k, v.toInt())
                is String -> e.putString(k, v)
            }
        }
        e.apply()
    }

    /** true = quick-share pe popup na aaye, seedha download ho. */
    fun directMode(c: Context) = sp(c).getBoolean("directMode", false)
    fun setDirectMode(c: Context, v: Boolean) = sp(c).edit().putBoolean("directMode", v).apply()

    /** direct mode me: true = audio (mp3), false = video. */
    fun audioMode(c: Context) = sp(c).getBoolean("audioMode", false)
    fun setAudioMode(c: Context, v: Boolean) = sp(c).edit().putBoolean("audioMode", v).apply()

    /** Engine last auto-update kis din hua (yyyyMMdd) — din me ek dafa update ke liye. */
    fun lastUpdateDay(c: Context): String = sp(c).getString("lastUpdateDay", "") ?: ""
    fun setLastUpdateDay(c: Context, v: String) = sp(c).edit().putString("lastUpdateDay", v).apply()

    /** Aakhri engine-update ka NATEEJA + version — pehle ye kahin record hi nahi hota tha,
     * is liye har dafa fail hone wala update bilkul kamyab jaisa dikhta tha (Engine.kt). */
    fun engineOutcome(c: Context): String = sp(c).getString("engineOutcome", "") ?: ""
    fun engineVersion(c: Context): String = sp(c).getString("engineVersion", "") ?: ""
    fun setEngineResult(c: Context, outcome: String, version: String) =
        sp(c).edit().putString("engineOutcome", outcome).putString("engineVersion", version).apply()

    /** Worker ka self-heal (engine update + ek retry) kis din chala — din me ek dafa.
     * Bagair is ke: 5 retries × kai downloads = baar baar ~10MB binary, mobile data pe. */
    fun lastSelfHealDay(c: Context): String = sp(c).getString("lastSelfHealDay", "") ?: ""
    fun setLastSelfHealDay(c: Context, v: String) = sp(c).edit().putString("lastSelfHealDay", v).apply()

    /** GitHub pe nayi app-version ka aakhri check (yyyyMMdd) + jo version mila. */
    fun lastAppCheckDay(c: Context): String = sp(c).getString("lastAppCheckDay", "") ?: ""
    fun setLastAppCheckDay(c: Context, v: String) = sp(c).edit().putString("lastAppCheckDay", v).apply()
    fun latestAppVersion(c: Context): String = sp(c).getString("latestAppVersion", "") ?: ""
    fun setLatestAppVersion(c: Context, v: String) = sp(c).edit().putString("latestAppVersion", v).apply()
    /** User ne kis version ka update-banner "baad me" kar diya. */
    fun dismissedAppVersion(c: Context): String = sp(c).getString("dismissedAppVersion", "") ?: ""
    fun setDismissedAppVersion(c: Context, v: String) = sp(c).edit().putString("dismissedAppVersion", v).apply()

    /** Instagram ka login expire ho gaya (Route 0 ko login page pe bheja gaya).
     * User ko batane ke liye — warna wo samajhta hai app toot gayi, jabki dobara
     * connect karna hai. */
    fun sessionExpired(c: Context) = sp(c).getBoolean("igSessionExpired", false)
    fun setSessionExpired(c: Context, v: Boolean) = sp(c).edit().putBoolean("igSessionExpired", v).apply()

    /** Battery-optimization exemption ek dafa pucha ya nahi. */
    fun askedBattery(c: Context) = sp(c).getBoolean("askedBattery", false)
    fun setAskedBattery(c: Context, v: Boolean) = sp(c).edit().putBoolean("askedBattery", v).apply()

    /** Data-saver (background data) wali setting ek dafa puchi ya nahi. */
    fun askedDataSaver(c: Context) = sp(c).getBoolean("askedDataSaver", false)
    fun setAskedDataSaver(c: Context, v: Boolean) = sp(c).edit().putBoolean("askedDataSaver", v).apply()

    /**
     * true (DEFAULT) = background queue: turant enqueue, notification me progress,
     * app band ho to bhi chalti (WorkManager + airlock FGS-lock + retry).
     * false = popup progress on screen (jo dekhna chahe).
     * Migration: purane installs pe bhi ek dafa true kar do (pehle popup default tha).
     */
    fun backgroundMode(c: Context): Boolean {
        val s = sp(c)
        if (!s.getBoolean("bgDefaultMigrated", false)) {
            s.edit().putBoolean("backgroundMode", true).putBoolean("bgDefaultMigrated", true).apply()
        }
        return s.getBoolean("backgroundMode", true)
    }
    fun setBackgroundMode(c: Context, v: Boolean) = sp(c).edit().putBoolean("backgroundMode", v).apply()

    /** Failed download kitni total attempts (1–5, default 5 = max — faisla 2026-07-09). */
    fun maxRetries(c: Context) = sp(c).getInt("maxRetries", 5).coerceIn(1, 5)
    fun setMaxRetries(c: Context, v: Int) = sp(c).edit().putInt("maxRetries", v.coerceIn(1, 5)).apply()

    /** Secret Vault feature ON/OFF (Privacy). ON = home-logo tap se vault khulta. */
    fun vaultEnabled(c: Context) = sp(c).getBoolean("vaultEnabled", true)
    fun setVaultEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("vaultEnabled", v).apply()

    /** V3: naye user ko out-of-the-box best settings — ek dafa inject. */
    fun ensureV3Defaults(c: Context) {
        val s = sp(c)
        if (!s.getBoolean("v3Defaults", false)) {
            s.edit()
                .putBoolean("embedThumbVideo", true)   // video me cover/thumbnail embed
                .putString("themeMode", "amoled")      // brand look = AMOLED black
                .putBoolean("dynamicColor", false)     // Steel Blue fixed (wallpaper-tint off)
                .putBoolean("v3Defaults", true)
                .apply()
        }
        // 2026-07-09: performance defaults PURANE installs pe bhi ek dafa inject —
        // 1080p quality + retry max (5) + parallel downloads max (5). User baad me
        // Settings se badle to dobara overwrite NAHI hota (flag ek hi dafa chalta).
        if (!s.getBoolean("perfDefaults", false)) {
            s.edit()
                .putString("quality", "1080")
                .putInt("maxRetries", 5)
                .putInt("simultaneous", 5)
                .putBoolean("perfDefaults", true)
                .apply()
        }
    }

    /** Turbo download — aria2c multi-connection engine (direct http files pe).
     * EXPERIMENTAL, default OFF — kuch networks/sites pe aria2c fail hota hai;
     * native engine (concurrent-fragments 8) already fast + 100% reliable. */
    fun turbo(c: Context) = sp(c).getBoolean("turbo", false)
    fun setTurbo(c: Context, v: Boolean) = sp(c).edit().putBoolean("turbo", v).apply()

    /** Background-mode info popup skip karo (default TRUE = share → seedha toast, zero friction).
     * Queue ab app band hone pe bhi chalti hai — popup ki zaroorat hi nahi. */
    fun hideBgWarning(c: Context) = sp(c).getBoolean("hideBgWarning", true)
    fun setHideBgWarning(c: Context, v: Boolean) = sp(c).edit().putBoolean("hideBgWarning", v).apply()

    /** Video quality: "best" | "2160" | "1080" | "720" | "480" | "360". Default 1080p
     *  (faisla 2026-07-09 — "best" kabhi 4K utha leta = slow + bhari files). */
    fun quality(c: Context): String = sp(c).getString("quality", "1080") ?: "1080"
    fun setQuality(c: Context, v: String) = sp(c).edit().putString("quality", v).apply()

    /** Video ke saath subtitles download + embed. */
    fun subs(c: Context) = sp(c).getBoolean("subs", false)
    fun setSubs(c: Context, v: Boolean) = sp(c).edit().putBoolean("subs", v).apply()

    /** Playlist link ho to poori playlist download (warna sirf ek video). */
    fun playlist(c: Context) = sp(c).getBoolean("playlist", false)
    fun setPlaylist(c: Context, v: Boolean) = sp(c).edit().putBoolean("playlist", v).apply()

    /** Pehli baar onboarding dikhaya ya nahi. */
    fun onboarded(c: Context) = sp(c).getBoolean("onboarded", false)
    fun setOnboarded(c: Context, v: Boolean) = sp(c).edit().putBoolean("onboarded", v).apply()

    // ==================== PHASE 1 — advanced settings ====================
    // ---- Format ----
    /** Audio: "best" | "mp3" | "m4a" | "opus" | "wav" | "flac". */
    fun audioFormat(c: Context): String = sp(c).getString("audioFormat", "mp3") ?: "mp3"
    fun setAudioFormat(c: Context, v: String) = sp(c).edit().putString("audioFormat", v).apply()

    /** Audio quality: "best" | "320" | "256" | "192" | "128". */
    fun audioQuality(c: Context): String = sp(c).getString("audioQuality", "best") ?: "best"
    fun setAudioQuality(c: Context, v: String) = sp(c).edit().putString("audioQuality", v).apply()

    /** Video container: "mp4" | "mkv" | "webm". */
    fun videoContainer(c: Context): String = sp(c).getString("videoContainer", "mp4") ?: "mp4"
    fun setVideoContainer(c: Context, v: String) = sp(c).edit().putString("videoContainer", v).apply()

    /** Multiple audio streams ko merge karo (video). */
    fun mergeAudio(c: Context) = sp(c).getBoolean("mergeAudio", false)
    fun setMergeAudio(c: Context, v: Boolean) = sp(c).edit().putBoolean("mergeAudio", v).apply()

    /** Video codec preference: "any" | "h264" | "vp9" | "av1". (soft-prefer, fallback safe) */
    fun videoCodec(c: Context): String = sp(c).getString("videoCodec", "any") ?: "any"
    fun setVideoCodec(c: Context, v: String) = sp(c).edit().putString("videoCodec", v).apply()

    /** Video ke andar thumbnail embed karo (cover art). */
    fun embedThumbVideo(c: Context) = sp(c).getBoolean("embedThumbVideo", false)
    fun setEmbedThumbVideo(c: Context, v: Boolean) = sp(c).edit().putBoolean("embedThumbVideo", v).apply()

    /** Video me chapters embed karo (agar source me hain). */
    fun embedChapters(c: Context) = sp(c).getBoolean("embedChapters", false)
    fun setEmbedChapters(c: Context, v: Boolean) = sp(c).edit().putBoolean("embedChapters", v).apply()

    // ---- Network ----
    /** Sirf Wi-Fi pe download (mobile data pe block). */
    fun wifiOnly(c: Context) = sp(c).getBoolean("wifiOnly", false)
    fun setWifiOnly(c: Context, v: Boolean) = sp(c).edit().putBoolean("wifiOnly", v).apply()

    /** Rate limit: "0" (off) | "500K" | "1M" | "2M" | "5M". */
    fun rateLimit(c: Context): String = sp(c).getString("rateLimit", "0") ?: "0"
    fun setRateLimit(c: Context, v: String) = sp(c).edit().putString("rateLimit", v).apply()

    /** Multi-thread (concurrent fragments) — tez download. Default ON. */
    fun multiThread(c: Context) = sp(c).getBoolean("multiThread", true)
    fun setMultiThread(c: Context, v: Boolean) = sp(c).edit().putBoolean("multiThread", v).apply()

    /** Saari connections IPv4 se. */
    fun forceIpv4(c: Context) = sp(c).getBoolean("forceIpv4", false)
    fun setForceIpv4(c: Context, v: Boolean) = sp(c).edit().putBoolean("forceIpv4", v).apply()

    /** Proxy URL (khali = off), e.g. socks5://127.0.0.1:1080 */
    fun proxy(c: Context): String = sp(c).getString("proxy", "") ?: ""
    fun setProxy(c: Context, v: String) = sp(c).edit().putString("proxy", v).apply()

    // ---- Files ----
    /** Filenames ko safe characters tak mehdood karo. Default ON. */
    fun restrictNames(c: Context) = sp(c).getBoolean("restrictNames", true)
    fun setRestrictNames(c: Context, v: Boolean) = sp(c).edit().putBoolean("restrictNames", v).apply()

    /** Download archive — pehle se download ki hui videos skip. */
    fun downloadArchive(c: Context) = sp(c).getBoolean("downloadArchive", false)
    fun setDownloadArchive(c: Context, v: Boolean) = sp(c).edit().putBoolean("downloadArchive", v).apply()

    /** Thumbnail alag file me bhi save. */
    fun saveThumb(c: Context) = sp(c).getBoolean("saveThumb", false)
    fun setSaveThumb(c: Context, v: Boolean) = sp(c).edit().putBoolean("saveThumb", v).apply()

    /** Custom output filename template (khali = default). yt-dlp -o tokens. */
    fun filenameTemplate(c: Context): String = sp(c).getString("filenameTemplate", "") ?: ""
    fun setFilenameTemplate(c: Context, v: String) = sp(c).edit().putString("filenameTemplate", v).apply()

    // ---- Privacy ----
    /** Incognito — download history save na ho. */
    fun incognito(c: Context) = sp(c).getBoolean("incognito", false)
    fun setIncognito(c: Context, v: Boolean) = sp(c).edit().putBoolean("incognito", v).apply()

    // ---- Look & feel (Phase 2) ----
    /** Theme: "system" | "light" | "dark" | "amoled". */
    fun themeMode(c: Context): String = sp(c).getString("themeMode", "system") ?: "system"
    fun setThemeMode(c: Context, v: String) = sp(c).edit().putString("themeMode", v).apply()

    /** Material You dynamic color (Android 12+). */
    fun dynamicColor(c: Context) = sp(c).getBoolean("dynamicColor", true)
    fun setDynamicColor(c: Context, v: Boolean) = sp(c).edit().putBoolean("dynamicColor", v).apply()

    // ---- Advanced / power (Phase 4) ----
    /** Trash auto-purge din ke baad: "0" (off) | "7" | "14" | "30". Default 30 = khud safai. */
    fun trashPurgeDays(c: Context): String = sp(c).getString("trashPurgeDays", "30") ?: "30"
    fun setTrashPurgeDays(c: Context, v: String) = sp(c).edit().putString("trashPurgeDays", v).apply()

    /** Playlist range (jab playlist ON) — e.g. "1-10" ya "3,5,7". Khali = poori. */
    fun playlistRange(c: Context): String = sp(c).getString("playlistRange", "") ?: ""
    fun setPlaylistRange(c: Context, v: String) = sp(c).edit().putString("playlistRange", v).apply()

    /** Custom yt-dlp flags (power users) — space-separated, har request pe. Khali = none. */
    fun customFlags(c: Context): String = sp(c).getString("customFlags", "") ?: ""
    fun setCustomFlags(c: Context, v: String) = sp(c).edit().putString("customFlags", v).apply()

    /** Clipboard se aakhri detect kiya gaya link (dobara suggest na karne ke liye). */
    fun lastClip(c: Context): String = sp(c).getString("lastClip", "") ?: ""
    fun setLastClip(c: Context, v: String) = sp(c).edit().putString("lastClip", v).apply()

    // ---- Storage / concurrency / v2 (Batch 3) ----
    /** Custom download folder (SAF tree uri). Khali = gallery default (Movies/Music). */
    fun customLocationUri(c: Context): String = sp(c).getString("customLocationUri", "") ?: ""
    fun setCustomLocationUri(c: Context, v: String) = sp(c).edit().putString("customLocationUri", v).apply()

    /** Ek waqt me kitni background downloads chalein (1–5). Default 5 = max (faisla 2026-07-09). */
    fun simultaneous(c: Context): Int = sp(c).getInt("simultaneous", 5)
    fun setSimultaneous(c: Context, v: Int) = sp(c).edit().putInt("simultaneous", v).apply()

    /** SponsorBlock — YouTube sponsor segments video se hata do. */
    fun sponsorBlock(c: Context) = sp(c).getBoolean("sponsorBlock", false)
    fun setSponsorBlock(c: Context, v: Boolean) = sp(c).edit().putBoolean("sponsorBlock", v).apply()

    /** Background-setup guide (battery + auto-start + recents-lock) user ne Done kiya?
     * XOS-type phones battery-exempt hone ke BAAD bhi auto-start ke bina freeze karte —
     * is liye banner exemption se azaad, is flag tak dikhta hai. */
    fun bgSetupDone(c: Context) = sp(c).getBoolean("bgSetupDone", false)
    fun setBgSetupDone(c: Context, v: Boolean) = sp(c).edit().putBoolean("bgSetupDone", v).apply()

    /** Master toggle: cookies use karni hain ya nahi. OFF = saved cookies bhi na bheje. Default ON. */
    fun cookiesEnabled(c: Context) = sp(c).getBoolean("cookiesEnabled", true)
    fun setCookiesEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("cookiesEnabled", v).apply()
}
