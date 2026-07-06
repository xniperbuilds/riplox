package com.xniperbuilds.downloader

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Backup / Restore — settings + history + trash + cookies ko ek JSON file me
 * export/import karta hai (naya phone / reinstall pe sab wapas).
 * ⚠️ Cookies bhi included hoti hain (login session) — backup file private rakho.
 */
object Backup {
    private const val VERSION = 1

    /** Sab kuch ek JSON string me (user isay file me save karega). */
    fun export(context: Context): String {
        val root = JSONObject()
        root.put("app", "XniperDownloader")
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        val prefs = JSONObject()
        Prefs.dumpInto(prefs, context)
        root.put("prefs", prefs)

        root.put("history", readArray(context, "history.json"))
        root.put("trash", readArray(context, "trash.json"))

        val ck = cookiesFile(context)
        if (ck.exists() && ck.length() > 0) root.put("cookies", ck.readText())

        return root.toString(2)
    }

    /** JSON backup se sab wapas restore. true = kaamyaab. */
    fun import(context: Context, json: String): Boolean {
        return try {
            val root = JSONObject(json)
            // Sirf APNI backup file accept karo — koi random JSON prefs me merge na ho
            if (root.optString("app") != "XniperDownloader") return false
            root.optJSONObject("prefs")?.let { Prefs.loadFrom(it, context) }
            root.optJSONArray("history")?.let { writeArray(context, "history.json", it) }
            root.optJSONArray("trash")?.let { writeArray(context, "trash.json", it) }
            if (root.has("cookies")) {
                val c = root.optString("cookies", "")
                if (c.isNotBlank()) cookiesFile(context).writeText(c)
            }
            true
        } catch (e: Exception) {
            Log.e("XniperDL", "backup import failed", e)
            false
        }
    }

    private fun readArray(c: Context, name: String): JSONArray {
        val f = File(c.filesDir, name)
        return if (f.exists()) {
            try { JSONArray(f.readText()) } catch (e: Exception) { JSONArray() }
        } else JSONArray()
    }

    private fun writeArray(c: Context, name: String, arr: JSONArray) {
        File(c.filesDir, name).writeText(arr.toString())
    }
}
