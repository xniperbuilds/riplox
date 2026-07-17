package com.xniperbuilds.downloader

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import java.io.File

/** Share ke text me se pehla http(s) link nikaalo. */
fun extractUrl(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val found = Regex("https?://\\S+").find(text)?.value ?: text.trim()
    // share-text me link ke aakhir me lagi punctuation hata do
    return found.trimEnd('.', ',', ')', ']', '!', '?', ';', '"', '\'')
}

/** Preview info (thumbnail + title) — download se pehle dikhane ke liye. */
data class Preview(val title: String, val uploader: String, val thumbnail: String?, val duration: Long)

fun getPreview(context: Context, link: String): Preview {
    val req = YoutubeDLRequest(link)
    req.addOption("--no-playlist")
    req.addOption("--no-warnings")
    req.addOption("--socket-timeout", "30")
    req.applyCommon(context)
    val info = YoutubeDL.getInstance().getInfo(req)
    val dur = try { info.duration.toLong() } catch (e: Exception) { 0L }
    return Preview(info.title ?: "—", info.uploader ?: "—", info.thumbnail, dur)
}

/** Codec preference → yt-dlp --format-sort value (soft-prefer; agar wo codec na ho to fallback). */
fun codecSort(codec: String): String? = when (codec) {
    "h264" -> "vcodec:h264"
    "vp9" -> "vcodec:vp9"
    "av1" -> "vcodec:av01"
    else -> null
}

// ============================================================================
// FORMAT PICKER — download se pehle available formats (resolution/size/codec)
// dikhane ke liye. yt-dlp ke -J (dump-json) se parse — library model getters pe
// depend nahi (robust). Sirf video-wale formats (resolution chooser).
// ============================================================================
data class FormatOption(
    val formatId: String,
    val height: Int,
    val ext: String,
    val filesize: Long,   // bytes; 0 = unknown
    val vcodec: String,
    val acodec: String,
    val note: String
) {
    val hasVideo get() = vcodec != "none" && vcodec.isNotBlank()
    val hasAudio get() = acodec != "none" && acodec.isNotBlank()
    /** Video-only ho to bestaudio merge; warna as-is. */
    fun formatArg(): String = if (hasVideo && !hasAudio) "$formatId+bestaudio/$formatId" else formatId
    fun label(): String {
        val res = when {
            height >= 2160 -> "4K"
            height > 0 -> "${height}p"
            else -> "video"
        }
        val size = if (filesize > 0) " · ${humanBytes(filesize)}" else ""
        val a = if (hasAudio) "" else " (video-only)"
        return "$res · $ext$size · ${vcodecShort(vcodec)}$a"
    }
}

fun vcodecShort(v: String): String = when {
    v.startsWith("avc") || v.startsWith("h264") -> "H.264"
    v.startsWith("vp09") || v.startsWith("vp9") -> "VP9"
    v.startsWith("av01") -> "AV1"
    v.startsWith("hev") || v.startsWith("h265") -> "H.265"
    else -> v.take(6)
}

/** Available (video) formats laao — download se pehle chunne ke liye. */
fun fetchFormats(context: Context, link: String): List<FormatOption> {
    val req = YoutubeDLRequest(link)
    req.addOption("-J")            // dump single JSON, no download
    req.addOption("--no-playlist")
    req.addOption("--no-warnings")
    req.addOption("--socket-timeout", "30")
    req.applyCommon(context)
    val resp = YoutubeDL.getInstance().execute(req)
    val root = JSONObject(resp.out)
    val arr = root.optJSONArray("formats") ?: return emptyList()
    val out = ArrayList<FormatOption>()
    for (i in 0 until arr.length()) {
        val f = arr.getJSONObject(i)
        val vcodec = f.optString("vcodec", "none")
        if (vcodec == "none" || vcodec.isBlank()) continue   // sirf video formats
        val fid = f.optString("format_id", "")
        if (fid.isBlank()) continue
        val size = if (f.has("filesize") && !f.isNull("filesize"))
            f.optLong("filesize", 0) else f.optLong("filesize_approx", 0)
        out.add(
            FormatOption(
                formatId = fid,
                height = f.optInt("height", 0),
                ext = f.optString("ext", "?"),
                filesize = size,
                vcodec = vcodec,
                acodec = f.optString("acodec", "none"),
                note = f.optString("format_note", "")
            )
        )
    }
    // resolution desc; ek height ke multiple ho to bade size wala pehle
    return out.sortedWith(compareByDescending<FormatOption> { it.height }.thenByDescending { it.filesize })
}

/** Quality choice → yt-dlp format string (max height cap, merged best video+audio). */
fun formatFor(quality: String): String = when (quality) {
    "2160" -> "bestvideo[height<=2160]+bestaudio/best[height<=2160]/best"
    "1080" -> "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best"
    "720" -> "bestvideo[height<=720]+bestaudio/best[height<=720]/best"
    "480" -> "bestvideo[height<=480]+bestaudio/best[height<=480]/best"
    "360" -> "bestvideo[height<=360]+bestaudio/best[height<=360]/best"
    else -> "best"
}

// ============================================================================
// COOKIES — YouTube bot-block (403/429) + age-restricted / private / members
// videos ke liye. User apni cookies.txt (Netscape format) import karta hai;
// wo yahan fixed path pe rehti hai aur HAR yt-dlp request ke sath jaati hai.
// ============================================================================
/** Import ki hui cookies.txt ka fixed path (app-private). */
fun cookiesFile(context: Context): File = File(context.filesDir, "cookies.txt")

/** Cookies import hui hain ya nahi (file mojood + khali nahi). */
fun hasCookies(context: Context): Boolean {
    val f = cookiesFile(context)
    return f.exists() && f.length() > 0
}

/** SAF se chuni hui cookies.txt ko app-private path pe copy karo.
 * Pehle VALIDATE — galat file (PDF/photo/koi aur text) import ho jaye to
 * har download cryptic error se fail hoti; isliye Netscape format check zaroori. */
fun importCookies(context: Context, uri: Uri): Boolean {
    return try {
        val tmp = File(context.cacheDir, "cookies_import.tmp")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        } ?: return false
        // Netscape cookies.txt = kam az kam ek non-comment line jisme 7 tab-separated fields hon
        val valid = tmp.useLines { lines ->
            lines.take(500).any { l ->
                !l.startsWith("#") && l.isNotBlank() && l.split('\t').size >= 7
            }
        }
        if (!valid) {
            tmp.delete()
            return false
        }
        tmp.copyTo(cookiesFile(context), overwrite = true)
        tmp.delete()
        hasCookies(context)
    } catch (e: Exception) {
        Log.e("XniperDL", "cookie import failed", e)
        false
    }
}

/** Imported cookies hata do. */
fun clearCookies(context: Context): Boolean {
    val f = cookiesFile(context)
    return if (f.exists()) f.delete() else true
}

/** Ek site ke liye: (cookie-domain -> jin URLs se cookies uthani hain). */
fun cookieGroupsFor(site: String, customUrl: String? = null): List<Pair<String, List<String>>> = when (site) {
    "youtube" -> listOf(
        ".youtube.com" to listOf("https://www.youtube.com", "https://m.youtube.com"),
        ".google.com" to listOf("https://accounts.google.com", "https://www.google.com")
    )
    "instagram" -> listOf(".instagram.com" to listOf("https://www.instagram.com", "https://instagram.com"))
    "tiktok" -> listOf(".tiktok.com" to listOf("https://www.tiktok.com", "https://tiktok.com"))
    "facebook" -> listOf(
        ".facebook.com" to listOf("https://www.facebook.com", "https://m.facebook.com"),
        ".fb.com" to listOf("https://fb.com")
    )
    else -> { // custom URL
        val host = try { Uri.parse(customUrl).host ?: "" } catch (e: Exception) { "" }
        val root = host.removePrefix("www.").removePrefix("m.")
        val domain = if (root.isNotEmpty()) ".$root" else host
        listOf(domain to listOf(customUrl ?: "https://$host"))
    }
}

/** Site ka login/start URL (WebView me pehle ye khulta).
 * Insta/FB DESKTOP UA ke sath khulte hain (Seal-style) → unka desktop login form
 * simple hai, blank/redirect wale mobile in-app raste bypass ho jate hain. */
fun cookieSiteUrl(site: String, customUrl: String? = null): String = when (site) {
    "youtube" -> "https://m.youtube.com"
    "instagram" -> "https://www.instagram.com/accounts/login/"
    // TikTok: seedha /login desktop pe bot-gate se BLANK hota hai — root kholo,
    // user upar se "Log in" dabaye to desktop modal form milta hai (reliable)
    "tiktok" -> "https://www.tiktok.com/"
    "facebook" -> "https://www.facebook.com/login/"
    else -> customUrl ?: "https://www.google.com"
}

/** WebView DESKTOP Chrome UA use kare (Seal ka nuskha — login reliable).
 * Sab sites desktop pe — mobile login pages WebView me bug-zada hote hain (TikTok ka
 * cursor-jump, Insta/FB blank). Sirf YouTube mobile pe (wo pehle se sahi chal raha). */
fun useDesktopUa(site: String): Boolean = site != "youtube"

/** Display-name → domain match (per-site disconnect + badge dono isi se). */
private fun domainMatchesSite(d: String, name: String): Boolean = when (name) {
    "YouTube" -> "youtube" in d || "google" in d
    "Instagram" -> "instagram" in d
    "TikTok" -> "tiktok" in d
    "Facebook" -> "facebook" in d || d == "fb.com"
    else -> d == name || d.endsWith(".$name")
}

/** Sirf EK site ki cookies hatao (per-site Disconnect). Baqi sites ki bachi rehti hain.
 * SOFT disconnect: hatayi hui lines archive me chali jati hain (Settings → Saved cookies)
 * — account app se disconnected, par data user ke control me (wahan se delete kar sakta). */
fun disconnectSite(context: Context, name: String): Boolean {
    if (!hasCookies(context)) return true
    return try {
        val f = cookiesFile(context)
        val kept = ArrayList<String>()
        val removed = ArrayList<String>()
        f.readLines().forEach { l ->
            if (l.isBlank() || l.startsWith("#")) return@forEach
            val d = l.substringBefore('\t').removePrefix(".").lowercase()
            if (domainMatchesSite(d, name)) removed.add(l) else kept.add(l)
        }
        if (removed.isNotEmpty()) {
            archiveFile(context).appendText(removed.joinToString("\n") + "\n")
        }
        if (kept.isEmpty()) {
            f.delete()
        } else {
            f.writeText(
                "# Netscape HTTP Cookie File\n# XniperBuilds Downloader\n" +
                    kept.joinToString("\n") + "\n"
            )
        }
        true
    } catch (e: Exception) {
        Log.e("XniperDL", "disconnect $name failed", e)
        false
    }
}

// ---------- REAL login detection (guest cookies ≠ connected) ----------
// WebView kholte hi sites GUEST cookies de deti hain — un se "connected ✓" dikhana WRONG tha.
// Connected = us site ki LOGIN/session cookie maujood ho.
private val LOGIN_COOKIE_NAMES = mapOf(
    "YouTube" to listOf("SAPISID", "__Secure-3PAPISID", "SID", "__Secure-1PSID"),
    "Instagram" to listOf("sessionid", "ds_user_id"),
    "TikTok" to listOf("sessionid", "sid_tt", "sessionid_ss"),
    "Facebook" to listOf("c_user", "xs")
)

private fun siteNameOfDomain(d: String): String = when {
    "youtube" in d || "google" in d -> "YouTube"
    "instagram" in d -> "Instagram"
    "tiktok" in d -> "TikTok"
    "facebook" in d || d == "fb.com" -> "Facebook"
    else -> d.removePrefix("www.")
}

/** Sirf wo sites jinki REAL login-cookie file me hai (✓ badge isi se). Custom sites = koi bhi cookie. */
fun connectedSites(context: Context): List<String> {
    if (!hasCookies(context)) return emptyList()
    return try {
        val names = LinkedHashSet<String>()
        cookiesFile(context).readLines().forEach { l ->
            if (l.isBlank() || l.startsWith("#")) return@forEach
            val parts = l.split('\t')
            if (parts.size < 7) return@forEach
            val d = parts[0].removePrefix(".").lowercase()
            val cookieName = parts[5]
            val value = parts[6]
            val site = siteNameOfDomain(d)
            val loginNames = LOGIN_COOKIE_NAMES[site]
            if (loginNames == null) {
                // custom/unknown site — koi bhi cookie = connected
                if (d.isNotBlank() && value.isNotBlank()) names.add(site)
            } else if (cookieName in loginNames && value.isNotBlank() && value != "\"\"") {
                names.add(site)
            }
        }
        names.toList()
    } catch (e: Exception) {
        emptyList()
    }
}

// ---------- Saved-cookies archive (soft-disconnect storage) ----------
private fun archiveFile(context: Context) = File(context.filesDir, "cookies_archive.txt")

/** Archive me kin sites ki cookies pari hain (display names). */
fun archivedSites(context: Context): List<String> {
    val f = archiveFile(context)
    if (!f.exists()) return emptyList()
    return try {
        val names = LinkedHashSet<String>()
        f.readLines().forEach { l ->
            if (l.isBlank() || l.startsWith("#")) return@forEach
            val d = l.substringBefore('\t').removePrefix(".").lowercase()
            when {
                "youtube" in d || "google" in d -> names.add("YouTube")
                "instagram" in d -> names.add("Instagram")
                "tiktok" in d -> names.add("TikTok")
                "facebook" in d || d == "fb.com" -> names.add("Facebook")
                d.isNotBlank() -> names.add(d.removePrefix("www."))
            }
        }
        names.toList()
    } catch (e: Exception) {
        emptyList()
    }
}

/** Archive se EK site ki cookies PERMANENT delete. */
fun deleteArchivedSite(context: Context, name: String): Boolean {
    val f = archiveFile(context)
    if (!f.exists()) return true
    return try {
        val kept = f.readLines().filter { l ->
            if (l.isBlank() || l.startsWith("#")) return@filter false
            val d = l.substringBefore('\t').removePrefix(".").lowercase()
            !domainMatchesSite(d, name)
        }
        if (kept.isEmpty()) f.delete() else f.writeText(kept.joinToString("\n") + "\n")
        true
    } catch (e: Exception) {
        false
    }
}

/** Poora archive delete. */
fun clearArchive(context: Context) {
    try { archiveFile(context).delete() } catch (_: Exception) {}
}

/**
 * Site-aware friendly error — khaas Instagram/TikTok/YouTube "login required" cases.
 * Insta 2025-26 se guests ke liye downloads block karta hai → connect kiye bina fail par
 * user ko clear wajah + hal batao (Nazim ka requirement).
 */
fun smartError(context: Context, link: String, raw: String?): String {
    val l = link.lowercase()
    val connected = try { connectedSites(context) } catch (e: Exception) { emptyList() }
    fun need(site: String) =
        "$site now blocks guest downloads (their new policy). Connect $site once — Settings → Connected accounts — then retry. Your password never touches this app."
    return when {
        "instagram" in l && !connected.contains("Instagram") -> need("Instagram")
        "tiktok" in l && !connected.contains("TikTok") &&
            (raw?.contains("403") == true || raw?.contains("not available", true) == true) -> need("TikTok")
        ("youtube" in l || "youtu.be" in l) && !connected.contains("YouTube") &&
            (raw?.contains("Sign in", true) == true || raw?.contains("age", true) == true ||
                raw?.contains("bot", true) == true) -> need("YouTube")
        else -> friendlyError(raw)
    }
}

/** cookies.txt me kin platforms ki cookies save hain — display names (badge ke liye). */
fun cookieSites(context: Context): List<String> {
    if (!hasCookies(context)) return emptyList()
    return try {
        val names = LinkedHashSet<String>()
        cookiesFile(context).readLines().forEach { l ->
            if (l.isBlank() || l.startsWith("#")) return@forEach
            val d = l.substringBefore('\t').removePrefix(".").lowercase()
            when {
                "youtube" in d || "google" in d -> names.add("YouTube")
                "instagram" in d -> names.add("Instagram")
                "tiktok" in d -> names.add("TikTok")
                "facebook" in d || d == "fb.com" -> names.add("Facebook")
                d.isNotBlank() -> names.add(d.removePrefix("www."))
            }
        }
        names.toList()
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * In-app WebView (CookieLoginActivity) me login/open ke baad wahan ki cookies ko
 * Netscape cookies.txt me save karo (yt-dlp isay --cookies se parhta).
 * MERGE karta hai — dusri sites (jaise pehle se YT) ki cookies bachi rehti, sirf isi
 * site ki refresh hoti. Return = is site ki kitni cookies save huin.
 */
fun saveCookiesFromWebView(context: Context, groups: List<Pair<String, List<String>>>): Int {
    val cm = CookieManager.getInstance()
    try { cm.flush() } catch (_: Exception) {}
    val siteDomains = groups.map { it.first }.toSet()
    // purani lines — is site ke domains hata do (taake refresh ho), baaki sites ki rehne do
    val kept = if (hasCookies(context)) {
        cookiesFile(context).readLines().filter { line ->
            line.isNotBlank() && !line.startsWith("#") &&
                siteDomains.none { d -> line.startsWith("$d\t") }
        }
    } else emptyList()

    val seen = HashSet<String>()
    val fresh = ArrayList<String>()
    val expiry = (System.currentTimeMillis() / 1000) + 10L * 365 * 24 * 3600 // ~10 saal
    for ((domain, urls) in groups) {
        for (url in urls) {
            val raw = cm.getCookie(url) ?: continue
            for (part in raw.split(";")) {
                val kv = part.trim()
                val eq = kv.indexOf('=')
                if (eq <= 0) continue
                val name = kv.substring(0, eq).trim()
                val value = kv.substring(eq + 1).trim()
                if (!seen.add("$domain\t$name")) continue
                fresh.add("$domain\tTRUE\t/\tTRUE\t$expiry\t$name\t$value")
            }
        }
    }
    if (fresh.isEmpty() && kept.isEmpty()) return 0
    val sb = StringBuilder("# Netscape HTTP Cookie File\n# XniperBuilds Downloader\n")
    kept.forEach { sb.append(it).append("\n") }
    fresh.forEach { sb.append(it).append("\n") }
    cookiesFile(context).writeText(sb.toString())
    return fresh.size
}

/** Har request pe common options — cookies + network flags.
 * NOTE: yt-dlp ke extractor (TikTok/YouTube) apne site-specific headers/UA use karte hain —
 * humne --user-agent/--referer override karke test kiya to TikTok TOOT gaya ("status code 0").
 * Isliye headers ko haath NAHI lagana. TikTok 403 ka asli fix = TikTok cookies (login) + engine update. */
private fun YoutubeDLRequest.applyCommon(context: Context) {
    if (Prefs.cookiesEnabled(context) && hasCookies(context)) addOption("--cookies", cookiesFile(context).absolutePath)
    if (Prefs.multiThread(context)) addOption("--concurrent-fragments", "8")
    // TURBO (experimental, default OFF): aria2c multi-connection — sirf direct http(s) files.
    // Android pe aria2c ke paas system CA certs nahi hote → check-certificate=false LAZMI
    // (iske bagair "aria2c exited with code 1"). HLS/DASH native engine pe hi rehte hain.
    if (Prefs.turbo(context)) {
        addOption("--downloader", "http:libaria2c.so")
        addOption("--external-downloader-args", "aria2c:--check-certificate=false -x 8 -k 1M")
    }
    val rl = Prefs.rateLimit(context)
    if (rl != "0" && rl.isNotBlank()) addOption("-r", rl)
    if (Prefs.forceIpv4(context)) addOption("--force-ipv4")
    val px = Prefs.proxy(context)
    if (px.isNotBlank()) addOption("--proxy", px)
    // Custom yt-dlp flags (power users) — "--flag value" pairs sahi tarah jud ke jayen
    val cf = Prefs.customFlags(context)
    if (cf.isNotBlank()) {
        val toks = cf.trim().split(Regex("\\s+"))
        var i = 0
        while (i < toks.size) {
            val t = toks[i]
            if (t.startsWith("-") && i + 1 < toks.size && !toks[i + 1].startsWith("-")) {
                addOption(t, toks[i + 1]); i += 2
            } else {
                addOption(t); i++
            }
        }
    }
}

/** yt-dlp ke lambe raw ERROR ko chhota, kaam-ka message banao (user ko agla qadam bhi bata do). */
fun friendlyError(raw: String?): String {
    val m = (raw ?: "").lowercase()
    return when {
        "instagram sent an empty media response" in m ->
            "Instagram needs login for this post.\nOpen the app → Connect Instagram → retry."
        "sign in to confirm" in m || "not a bot" in m ->
            "YouTube is asking for login (bot check).\nOpen the app → Connect YouTube → retry."
        ("age" in m && "restrict" in m) ->
            "Age-restricted — Connect that site once in the app, then retry."
        ("private" in m && ("login" in m || "available" in m)) ->
            "Private post — Connect an account that can see it (in the app)."
        "http error 403" in m ->
            "Site blocked the request (403). Connect that site in the app, or retry later."
        "unsupported url" in m -> "This link type isn't supported."
        "no space left" in m -> "Phone storage is full."
        ("timed out" in m || "unable to connect" in m || "network is unreachable" in m) ->
            "Network problem — check internet and retry."
        else -> (raw ?: "Unknown error").take(220)
    }
}

/** Active network metered (mobile data) hai? Wi-Fi-only check ke liye. */
fun isMetered(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    return cm.isActiveNetworkMetered
}

/** Temp/cache files delete karo — freed bytes return. */
fun clearTempFiles(context: Context): Long {
    val base = context.getExternalFilesDir("temp") ?: return 0L
    var freed = 0L
    base.listFiles()?.forEach { f ->
        freed += f.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        f.deleteRecursively()
    }
    return freed
}

/** Startup-safai ka SAFE variant — sirf 24h+ purane dl_* folders (naam me timestamp hai).
 * ⚠️ Poora clearTempFiles startup pe race karta tha: ENQUEUED job app-open pe usi second
 * RUNNING hoti thi aur uska taaza temp folder cleanup uDa deta tha → download "chalti"
 * par file gayab → File not found / 100% stuck. Age-check se race namumkin. */
fun clearStaleTempFiles(context: Context, olderThanMs: Long = 24 * 60 * 60 * 1000L): Long {
    val base = context.getExternalFilesDir("temp") ?: return 0L
    val cutoff = System.currentTimeMillis() - olderThanMs
    var freed = 0L
    base.listFiles()?.forEach { f ->
        val ts = f.name.removePrefix("dl_").substringBefore('_').toLongOrNull()
        val stale = if (ts != null) ts < cutoff else f.lastModified() < cutoff
        if (stale) {
            freed += f.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            f.deleteRecursively()
        }
    }
    return freed
}

// ============================================================================
// POPUP MODE (default) — poora yt-dlp download + gallery save, live progress.
// Activity FOREGROUND rehte call karo. Har site chalti (IG/TikTok samet).
// ============================================================================
fun runDownload(
    context: Context,
    link: String,
    audioOnly: Boolean,
    formatOverride: String? = null,
    processId: String? = null,   // Cancel ke liye — YoutubeDL.destroyProcessById(processId)
    // Per-download overrides ("Download Options" share-tile se) — null = global Settings
    qualityOverride: String? = null,
    playlistOverride: Boolean? = null,
    subsOverride: Boolean? = null,
    audioFormatOverride: String? = null,
    onBeat: () -> Unit = {},     // har yt-dlp output pe fire — stall-watchdog ka signal
    onSave: (Int) -> Unit = {},  // gallery/custom-save copy progress (0–100) — "Finishing" phase visible + beats
    onProgress: (Int) -> Unit
): String {
    Log.i("XniperDL", "runDownload audio=$audioOnly ${link.take(50)}")
    if (Prefs.wifiOnly(context) && isMetered(context)) {
        throw Exception("Wi-Fi only is ON — downloads on mobile data are blocked. Connect to Wi-Fi or turn it off in Settings.")
    }
    val base = context.getExternalFilesDir("temp") ?: context.filesDir
    val work = File(base, "dl_${System.currentTimeMillis()}_${(0..9999).random()}")
    work.mkdirs()
    try {
        val req = YoutubeDLRequest(link)
        val tmpl = Prefs.filenameTemplate(context).ifBlank { "%(uploader)s_%(id)s_XniperBuilds.%(ext)s" }
        req.addOption("-o", "${work.absolutePath}/$tmpl")
        if (audioOnly) {
            req.addOption("-f", "bestaudio/best")
            req.addOption("-x")
            val af = audioFormatOverride ?: Prefs.audioFormat(context)
            req.addOption("--audio-format", af) // "best" bhi valid
            val aq = Prefs.audioQuality(context)
            req.addOption("--audio-quality", if (aq == "best") "0" else "${aq}K")
            req.addOption("--embed-thumbnail")
            req.addOption("--embed-metadata")
        } else {
            if (formatOverride != null) {
                req.addOption("-f", formatOverride)
            } else {
                req.addOption("-f", formatFor(qualityOverride ?: Prefs.quality(context)))
                codecSort(Prefs.videoCodec(context))?.let { req.addOption("-S", it) }
            }
            req.addOption("--merge-output-format", Prefs.videoContainer(context))
            req.addOption("--embed-metadata")
            if (Prefs.embedThumbVideo(context)) req.addOption("--embed-thumbnail")
            if (Prefs.embedChapters(context)) req.addOption("--embed-chapters")
            if (Prefs.sponsorBlock(context)) req.addOption("--sponsorblock-remove", "all")
            if (Prefs.mergeAudio(context)) req.addOption("--audio-multistreams")
            if (subsOverride ?: Prefs.subs(context)) {
                req.addOption("--write-subs")
                req.addOption("--write-auto-subs")
                req.addOption("--sub-langs", "en.*")
                req.addOption("--embed-subs")
            }
        }
        // Format picker se aayi format-id sirf US video ki hoti hai — playlist pe apply nahi ho sakti
        if (!(playlistOverride ?: Prefs.playlist(context)) || formatOverride != null) {
            req.addOption("--no-playlist")
        } else {
            val range = Prefs.playlistRange(context)
            if (range.isNotBlank()) req.addOption("--playlist-items", range)
        }
        req.addOption("--no-warnings")
        if (Prefs.restrictNames(context)) req.addOption("--restrict-filenames")
        req.addOption("--socket-timeout", "30")
        if (Prefs.saveThumb(context)) req.addOption("--write-thumbnail")
        if (Prefs.downloadArchive(context)) {
            req.addOption("--download-archive", File(context.filesDir, "archive.txt").absolutePath)
        }
        req.applyCommon(context)

        var maxP = 0
        YoutubeDL.getInstance().execute(req, processId) { progress, _, _ ->
            onBeat() // process zinda hai — watchdog timer reset
            val p = progress.toInt()
            if (p in 0..100 && p > maxP) {
                maxP = p
                onProgress(maxP)
            }
        }

        // SAB downloaded files uthao (playlist = multiple) — partial/temp files nahi
        val all = work.walkTopDown().filter { it.isFile }.filterNot {
            val n = it.name.lowercase()
            n.endsWith(".part") || n.endsWith(".ytdl") || n.endsWith(".tmp") || n.endsWith(".json")
        }.toList()
        val videoExts = setOf("mp4", "mkv", "webm", "mov", "m4v", "ts", "3gp", "avi")
        val audioExts = setOf("mp3", "m4a", "opus", "ogg", "wav", "flac", "aac", "weba")
        val imageExts = setOf("jpg", "jpeg", "png", "webp")
        val media = all.filter {
            val e = it.extension.lowercase()
            if (audioOnly) e in audioExts || e in videoExts else e in videoExts
        }
        val thumbs = all.filter { it.extension.lowercase() in imageExts }
        if (media.isEmpty()) {
            // Archive ON ho to yt-dlp pehle-se-download ki video skip kar deta → koi file nahi
            if (Prefs.downloadArchive(context)) return "Already downloaded (archive skip)."
            throw Exception("File not found")
        }
        val platform = platformFolder(link)
        val custom = Prefs.customLocationUri(context)
        var firstDisplay = ""
        var saved = 0
        for (file in media) {
            val fname = file.name
            // Save-copy ke "beats": har chunk pe watchdog reset + pct-change pe onSave —
            // bade file ki gallery-copy ab na watchdog se marti hai na "100% stuck" dikhti.
            val total = file.length().coerceAtLeast(1)
            var lastSaveP = -1
            val onCopy: (Long) -> Unit = { copied ->
                onBeat()
                val sp = ((copied * 100) / total).toInt().coerceIn(0, 100)
                if (sp != lastSaveP) {
                    lastSaveP = sp
                    onSave(sp)
                }
            }
            val galleryDisplay = "${if (audioOnly) "Music" else "Movies"}/XniperBuilds/$platform/$fname"
            val (location, display) = if (custom.isNotBlank()) {
                try {
                    saveToCustomTree(context, file, platform, Uri.parse(custom), audioOnly, onCopy) to
                        "Custom folder/XniperBuilds/$platform/$fname"
                } catch (e: Exception) {
                    Log.e("XniperDL", "custom save fail → gallery", e)
                    savePublic(context, file, platform, audioOnly, onCopy) to "$galleryDisplay (custom fail → gallery)"
                }
            } else {
                savePublic(context, file, platform, audioOnly, onCopy) to galleryDisplay
            }
            if (!Prefs.incognito(context)) {
                History.add(context, link, fname, platform, location, audioOnly)
            }
            if (saved == 0) firstDisplay = display
            saved++
        }
        // "Save thumbnail as file" ON → thumbnail Pictures me bhi save karo
        if (Prefs.saveThumb(context)) {
            for (t in thumbs) {
                try {
                    saveImageToPictures(context, t, platform)
                } catch (e: Exception) {
                    Log.e("XniperDL", "thumb save fail", e)
                }
            }
        }
        return if (saved == 1) firstDisplay
        else "$saved files → ${if (custom.isNotBlank()) "Custom folder" else if (audioOnly) "Music" else "Movies"}/XniperBuilds/$platform/"
    } finally {
        work.deleteRecursively()
    }
}

// ============================================================================
// DIRECT URL EXTRACT — Player me online stream ke liye (ExoPlayer ko direct
// media URL chahiye hota hai).
// ============================================================================
data class DirectMedia(
    val url: String,
    val filename: String,
    val platform: String,
    val headers: Map<String, String>
)

fun extractDirect(context: Context, link: String, audioOnly: Boolean): DirectMedia {
    Log.i("XniperDL", "extractDirect audio=$audioOnly ${link.take(50)}")
    val req = YoutubeDLRequest(link)
    req.addOption("-f", if (audioOnly) "bestaudio/best" else "best")
    req.addOption("--no-playlist")
    req.addOption("--no-warnings")
    req.addOption("--socket-timeout", "30")
    req.applyCommon(context)
    val info = YoutubeDL.getInstance().getInfo(req)
    val url = info.url ?: throw Exception("No direct video URL for this link (background mode doesn't work on this site)")
    val ext = info.ext ?: if (audioOnly) "m4a" else "mp4"
    val uploader = (info.uploader ?: "video").replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
    val id = (info.id ?: "vid").replace(Regex("[^A-Za-z0-9._-]"), "_").take(30)
    val filename = "${uploader}_${id}_XniperBuilds.$ext"
    val headers: Map<String, String> = info.httpHeaders ?: emptyMap()
    return DirectMedia(url, filename, platformFolder(link), headers)
}
