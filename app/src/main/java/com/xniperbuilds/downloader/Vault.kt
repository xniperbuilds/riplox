package com.xniperbuilds.downloader

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Vault ka ek item. file = filesDir/vault ke andar ka naam. */
data class VaultItem(
    val id: Long,
    val title: String,
    val file: String,
    val platform: String,
    val isAudio: Boolean,
    val time: Long
)

/**
 * Secret Vault — videos app-private folder me (gallery/file-manager me INVISIBLE).
 * Lock VaultActivity handle karti hai (fingerprint/PIN); ye sirf storage hai.
 * Backup me shamil NAHI hota (private rehta hai).
 */
object Vault {
    private const val INDEX = "vault.json"

    private fun dir(c: Context) = File(c.filesDir, "vault").apply { mkdirs() }
    private fun indexFile(c: Context) = File(c.filesDir, INDEX)

    private fun read(c: Context): MutableList<VaultItem> {
        val f = indexFile(c)
        if (!f.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(f.readText())
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                VaultItem(
                    o.getLong("id"), o.getString("title"), o.getString("file"),
                    o.optString("platform", "Other"), o.optBoolean("isAudio", false),
                    o.getLong("time")
                )
            }
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun write(c: Context, list: List<VaultItem>) {
        val arr = JSONArray()
        list.forEach { v ->
            arr.put(JSONObject().apply {
                put("id", v.id); put("title", v.title); put("file", v.file)
                put("platform", v.platform); put("isAudio", v.isAudio); put("time", v.time)
            })
        }
        indexFile(c).writeText(arr.toString())
    }

    fun items(c: Context): List<VaultItem> = read(c)
    fun fileOf(c: Context, item: VaultItem) = File(dir(c), item.file)

    /**
     * History record ko vault me le jao: file copy → private dir, original gallery se delete,
     * history se record silently remove. Return false = copy fail (original untouched).
     */
    @Synchronized
    fun moveIn(c: Context, rec: DownloadRecord): Boolean {
        return try {
            val ext = rec.location.substringAfterLast('.', "").take(5).ifBlank { if (rec.isAudio) "mp3" else "mp4" }
            val safe = rec.title.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)
            val destName = "${rec.id}_$safe.$ext"
            val dest = File(dir(c), destName)
            // copy (MediaStore content:// ya seedha path — dono)
            if (rec.location.startsWith("content://")) {
                c.contentResolver.openInputStream(Uri.parse(rec.location))?.use { inp ->
                    dest.outputStream().use { inp.copyTo(it) }
                } ?: return false
            } else {
                File(rec.location).inputStream().use { inp ->
                    dest.outputStream().use { inp.copyTo(it) }
                }
            }
            if (!dest.exists() || dest.length() == 0L) return false
            // original delete (best-effort) + history se hatao
            try {
                if (rec.location.startsWith("content://")) {
                    c.contentResolver.delete(Uri.parse(rec.location), null, null)
                } else {
                    File(rec.location).delete()
                }
            } catch (_: Exception) {
            }
            History.removeFromHistory(c, rec.id)
            val list = read(c)
            list.add(0, VaultItem(rec.id, rec.title, destName, rec.platform, rec.isAudio, System.currentTimeMillis()))
            write(c, list)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Vault se wapas gallery me (Movies/Music → XniperBuilds) + history entry wapas. */
    @Synchronized
    fun restore(c: Context, id: Long): Boolean {
        val item = read(c).find { it.id == id } ?: return false
        val src = fileOf(c, item)
        if (!src.exists()) return false
        return try {
            val uri = if (item.isAudio) saveAudioToMusic(c, src, item.platform)
            else saveVideoToGallery(c, src, item.platform)
            History.add(c, "", item.title, item.platform, uri, item.isAudio)
            src.delete()
            val list = read(c); list.removeAll { it.id == id }; write(c, list)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Permanent delete (file + index). */
    @Synchronized
    fun delete(c: Context, id: Long): Boolean {
        val item = read(c).find { it.id == id } ?: return true
        try { fileOf(c, item).delete() } catch (_: Exception) {}
        val list = read(c); list.removeAll { it.id == id }; write(c, list)
        return true
    }
}
