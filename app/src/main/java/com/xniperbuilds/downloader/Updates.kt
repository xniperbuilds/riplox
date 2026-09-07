package com.xniperbuilds.downloader

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Nayi version aa gayi hai" — GitHub Releases se.
 *
 * KYUN HAATH SE: Play ka in-app-update API (jo Riplox TT me hai) yahan chal hi nahi sakta —
 * ye app Play pe nahi hai, GitHub Releases se milti hai. Us ka matlab ye tha ke ek user jis
 * ne v1.0.0 install ki thi, wo aaj tak wahi chala raha hai aur usay kabhi pata nahi chala ke
 * do version aa chuke hain. Engine khud ko update kar leta hai, app khud ko nahi kar sakti.
 *
 * ⚠️ Ye khud kuch install NAHI karta aur na kar sakta hai (sideload = user ka apna faisla,
 * aur chup-chaap APK utaarna wo cheez hai jo malware karta hai). Ye sirf batata hai, aur
 * Releases ka safha khol deta hai.
 *
 * ⚠️ GitHub ka anonymous API IP ke hisaab se **60 request/ghanta** deta hai. Is liye check
 * din me ek dafa hai aur nateeja Prefs me rakha jata hai — har app-open pe nahi.
 */
object Updates {

    private const val API =
        "https://api.github.com/repos/xniperbuilds/riplox/releases/latest"

    /**
     * "Get it" ka target — **seedha APK**, releases ka safha nahi.
     *
     * `releases/latest/download/<naam jo kabhi na badle>` hamesha nayi release ki usi naam wali
     * file pe le jata hai, aur browser usay foran download karna shuru kar deta hai. Safha
     * kholne pe user ko khud assets me se APK dhoondni parti thi — ghair-technical banda wahan
     * "Source code (zip)" pe tap kar deta hai.
     *
     * ⚠️ Is ka matlab: har release me `Riplox.apk` naam ki copy **hamesha** honi chahiye. Wo naam
     * badla, ya kisi release me chhoot gaya, to har purane user ka update button toot jayega.
     */
    const val DOWNLOAD_URL = "https://github.com/xniperbuilds/riplox/releases/latest/download/Riplox.apk"

    /** Jab user poori release parhna chahe (What's new). */
    const val RELEASES_PAGE = "https://github.com/xniperbuilds/riplox/releases/latest"

    private fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    /** "v1.1.0" / "1.1.0" → [1, 1, 0]. Jo samajh na aaye wo khali list. */
    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").split('.', '-', '_')
            .mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }

    /**
     * `latest` `current` se naya hai?
     *
     * ⚠️ String compare kaam nahi deta — "1.10.0" < "1.9.0" nikalta hai. Har hissa ginna
     * hi wahid sahi tareeqa hai.
     */
    fun isNewer(latest: String, current: String): Boolean {
        val a = parts(latest)
        val b = parts(current)
        if (a.isEmpty() || b.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * Din me ek dafa GitHub se poochho. Nateeja Prefs me; UI usay parhta hai.
     *
     * Kabhi throw nahi karta: ye upkeep hai, app ka kaam nahi. Net na ho to bas agli dafa.
     */
    suspend fun checkDaily(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (Prefs.lastAppCheckDay(app) == today()) return@withContext
        // Wi-Fi-only ON + mobile data → rehne do. ~1KB hi sahi, user ka faisla user ka hai.
        if (Prefs.wifiOnly(app) && isMetered(app)) return@withContext
        try {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Riplox-Android")
            }
            val code = conn.responseCode
            if (code != 200) {
                // 403 = rate limit (60/ghanta per IP). Din ka marker phir bhi lagao,
                // warna har app-open us deewar se takrata rahega.
                Log.w("XniperDL", "update check http $code")
                Prefs.setLastAppCheckDay(app, today())
                return@withContext
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val tag = JSONObject(body).optString("tag_name").orEmpty()
            if (tag.isNotBlank()) Prefs.setLatestAppVersion(app, tag.removePrefix("v"))
            Prefs.setLastAppCheckDay(app, today())
        } catch (e: Exception) {
            Log.w("XniperDL", "update check failed: ${e.message}")
        }
    }

    /**
     * Ab jo version maujood hai us se koi nayi version bahar hai? (Aur user ne usay pehle
     * "baad me" nahi kiya.) Blank = kuch dikhane ki zaroorat nahi.
     */
    fun pendingVersion(context: Context, currentVersionName: String): String {
        val latest = Prefs.latestAppVersion(context)
        if (latest.isBlank()) return ""
        if (Prefs.dismissedAppVersion(context) == latest) return ""
        return if (isNewer(latest, currentVersionName)) latest else ""
    }
}
