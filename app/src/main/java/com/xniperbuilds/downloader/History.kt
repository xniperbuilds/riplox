package com.xniperbuilds.downloader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Ek download ka record (history/trash ke liye). location = saved file ka URI/path.
 * trashedAt = trash me DAALNE ka waqt (auto-purge isi se ginta hai, download-time se nahi). */
data class DownloadRecord(
    val id: Long,
    val title: String,
    val platform: String,
    val url: String,
    val location: String,
    val isAudio: Boolean,
    val time: Long,
    val trashedAt: Long = 0
)

/** Download history + trash — simple JSON files me (app internal storage). */
object History {
    private const val HIST = "history.json"
    private const val TRASH = "trash.json"

    private fun file(c: Context, name: String) = File(c.filesDir, name)

    private fun read(c: Context, name: String): MutableList<DownloadRecord> {
        val f = file(c, name)
        if (!f.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(f.readText())
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                DownloadRecord(
                    o.getLong("id"),
                    o.getString("title"),
                    o.getString("platform"),
                    o.getString("url"),
                    o.getString("location"),
                    o.optBoolean("isAudio", false),
                    o.getLong("time"),
                    o.optLong("trashedAt", 0)
                )
            }
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun write(c: Context, name: String, list: List<DownloadRecord>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("title", r.title)
                put("platform", r.platform)
                put("url", r.url)
                put("location", r.location)
                put("isAudio", r.isAudio)
                put("time", r.time)
                put("trashedAt", r.trashedAt)
            })
        }
        file(c, name).writeText(arr.toString())
    }

    @Synchronized
    fun add(c: Context, url: String, title: String, platform: String, location: String, isAudio: Boolean) {
        val now = System.currentTimeMillis()
        val list = read(c, HIST)
        list.add(0, DownloadRecord(now, title, platform, url, location, isAudio, now))
        write(c, HIST, list)
    }

    fun all(c: Context): List<DownloadRecord> = read(c, HIST)
    fun trash(c: Context): List<DownloadRecord> = read(c, TRASH)

    /** History se hata ke trash me daalo (asli file bhi delete — space bache; URL trash me rehta re-download ke liye). */
    @Synchronized
    fun moveToTrash(c: Context, id: Long) {
        val hist = read(c, HIST)
        val item = hist.find { it.id == id } ?: return
        hist.removeAll { it.id == id }
        write(c, HIST, hist)
        val tr = read(c, TRASH)
        tr.add(0, item.copy(trashedAt = System.currentTimeMillis()))
        write(c, TRASH, tr)
        // asli file delete (best-effort)
        try {
            if (item.location.startsWith("content://")) {
                c.contentResolver.delete(android.net.Uri.parse(item.location), null, null)
            } else {
                File(item.location).delete()
            }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun restore(c: Context, id: Long) {
        val tr = read(c, TRASH)
        val item = tr.find { it.id == id } ?: return
        tr.removeAll { it.id == id }
        write(c, TRASH, tr)
        val hist = read(c, HIST)
        hist.add(0, item)
        write(c, HIST, hist)
    }

    /** History se chupchaap hatao (trash me NAHI — vault-move ke liye; file untouched). */
    @Synchronized
    fun removeFromHistory(c: Context, id: Long) {
        val hist = read(c, HIST)
        hist.removeAll { it.id == id }
        write(c, HIST, hist)
    }

    @Synchronized
    fun deleteForever(c: Context, id: Long) {
        val tr = read(c, TRASH)
        tr.removeAll { it.id == id }
        write(c, TRASH, tr)
    }

    /** Auto-purge — trash me daale hue X din guzre to permanent delete. Return = kitni hatayi.
     * trashedAt se ginta hai (purane records jinme trashedAt nahi tha → download time fallback). */
    @Synchronized
    fun purgeOldTrash(c: Context, days: Int): Int {
        if (days <= 0) return 0
        val cutoff = System.currentTimeMillis() - days.toLong() * 24 * 3600 * 1000
        val tr = read(c, TRASH)
        val old = tr.filter { (if (it.trashedAt > 0) it.trashedAt else it.time) < cutoff }
        if (old.isEmpty()) return 0
        tr.removeAll(old.toSet())
        write(c, TRASH, tr)
        return old.size
    }
}
