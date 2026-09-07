package com.xniperbuilds.downloader

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale

/** URL se platform ka naam nikaalo — folder banane ke liye. */
fun platformFolder(url: String): String {
    val u = url.lowercase()
    return when {
        "youtube.com" in u || "youtu.be" in u -> "YouTube"
        "instagram.com" in u -> "Instagram"
        "tiktok.com" in u -> "TikTok"
        "facebook.com" in u || "fb.watch" in u -> "Facebook"
        "twitter.com" in u || "x.com" in u -> "Twitter"
        "pinterest" in u -> "Pinterest"
        "reddit.com" in u -> "Reddit"
        "dailymotion" in u -> "Dailymotion"
        "vimeo.com" in u -> "Vimeo"
        "snapchat.com" in u -> "Snapchat"
        else -> "Other"
    }
}

/** Chunk-wise copy + har chunk pe onCopy(totalBytesCopied) — save-phase ke "beats".
 * Iske bagair bada file copy watchdog/notif ke liye 100% pe "khamosh maut" tha. */
private fun copyChunked(input: java.io.InputStream, out: java.io.OutputStream, onCopy: (Long) -> Unit) {
    val buf = ByteArray(256 * 1024)
    var total = 0L
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        out.write(buf, 0, n)
        total += n
        onCopy(total)
    }
    out.flush()
}

/** MediaStore me file likho (IS_PENDING flow) — fail ho to adhoori row delete (orphan na bache). */
private fun insertMedia(
    context: Context,
    contentUri: Uri,
    temp: File,
    mime: String,
    relPath: String,
    onCopy: (Long) -> Unit = {}
): String {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, temp.name)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = resolver.insert(contentUri, values) ?: throw Exception("MediaStore insert failed")
    try {
        resolver.openOutputStream(uri)?.use { out ->
            temp.inputStream().use { input -> copyChunked(input, out, onCopy) }
        } ?: throw Exception("Output stream null")
        val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        resolver.update(uri, done, null, null)
    } catch (e: Exception) {
        try { resolver.delete(uri, null, null) } catch (_: Exception) {}
        throw e
    }
    temp.delete()
    return uri.toString()
}

/** API 29+ : temp file ko Movies/XniperBuilds/<platform>/ me copy (gallery-visible). */
fun saveVideoToGallery(context: Context, temp: File, platform: String, onCopy: (Long) -> Unit = {}): String = insertMedia(
    context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, temp,
    mimeForExt(temp.extension, audioOnly = false), "${Environment.DIRECTORY_MOVIES}/XniperBuilds/$platform", onCopy
)

/** API 29+ : audio ko Music/XniperBuilds/<platform>/ me copy. */
fun saveAudioToMusic(context: Context, temp: File, platform: String, onCopy: (Long) -> Unit = {}): String = insertMedia(
    context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, temp,
    mimeForExt(temp.extension, audioOnly = true), "${Environment.DIRECTORY_MUSIC}/XniperBuilds/$platform", onCopy
)

/** Jo extensions tasveer hain — thumbnail bhi, aur photo-post ki asli file bhi. */
val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp")

fun isImageExt(ext: String): Boolean = ext.lowercase() in IMAGE_EXTS

/** Tasveer ko Pictures/XniperBuilds/<platform>/ me save karo (thumbnail bhi, photo post bhi). */
fun saveImageToPictures(context: Context, temp: File, platform: String, onCopy: (Long) -> Unit = {}): String {
    if (Build.VERSION.SDK_INT < 29) {
        return saveLegacyPublic(context, temp, platform, Environment.DIRECTORY_PICTURES, onCopy)
    }
    return insertMedia(
        context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, temp,
        mimeForExt(temp.extension, audioOnly = false),
        "${Environment.DIRECTORY_PICTURES}/XniperBuilds/$platform", onCopy
    )
}

/**
 * Public save — HAR Android version pe file mehfooz rahe.
 * API 29+ = MediaStore. API 26–28 = public folder me seedha copy + media scan
 * (WRITE permission na ho to app ke apne external folder me — file phir bhi bachti hai).
 */
fun savePublic(context: Context, temp: File, platform: String, audioOnly: Boolean, onCopy: (Long) -> Unit = {}): String =
    // ⚠️ EXTENSION pehle, `audioOnly` baad me. Photo post (Insta carousel / TT slideshow /
    // Twitter image) ki .jpg audioOnly=false ke sath aati hai — bina is check ke wo
    // MediaStore.VIDEO me `video/mp4` mime ke sath jati thi: gallery me toota hua entry,
    // Movies/ me pari hui tasveer, aur History me "video" jo tap pe player me nahi khulti.
    if (isImageExt(temp.extension)) {
        saveImageToPictures(context, temp, platform, onCopy)
    } else if (Build.VERSION.SDK_INT >= 29) {
        if (audioOnly) saveAudioToMusic(context, temp, platform, onCopy)
        else saveVideoToGallery(context, temp, platform, onCopy)
    } else {
        saveLegacyPublic(
            context, temp, platform,
            if (audioOnly) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES,
            onCopy
        )
    }

/** API 26–28: RELATIVE_PATH nahi hota — file copy + MediaScanner. Kabhi delete-without-copy nahi. */
@Suppress("DEPRECATION")
fun saveLegacyPublic(context: Context, temp: File, platform: String, publicDirType: String, onCopy: (Long) -> Unit = {}): String {
    val canWrite = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
    val dir = if (canWrite) {
        File(Environment.getExternalStoragePublicDirectory(publicDirType), "XniperBuilds/$platform")
    } else {
        // Permission nahi mili → app ka apna external folder (file manager se milta hai, delete nahi hoti)
        File(context.getExternalFilesDir(publicDirType), platform)
    }
    dir.mkdirs()
    var dest = File(dir, temp.name)
    var i = 1
    while (dest.exists()) {
        dest = File(dir, "${temp.nameWithoutExtension}_$i.${temp.extension}")
        i++
    }
    dest.outputStream().use { out ->
        temp.inputStream().use { input -> copyChunked(input, out, onCopy) }
    }
    temp.delete()
    try {
        MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
    } catch (_: Exception) {
    }
    return dest.absolutePath
}

// ============================================================================
// CUSTOM DOWNLOAD LOCATION — user SAF se koi folder / SD card chunta hai; wahan
// platform subfolder bana ke file copy karte hain (DocumentsContract = no extra dep).
// ============================================================================
fun mimeForExt(ext: String, audioOnly: Boolean): String = when (ext.lowercase()) {
    "mp4", "m4v" -> "video/mp4"
    "webm" -> if (audioOnly) "audio/webm" else "video/webm"
    "weba" -> "audio/webm"
    "mkv" -> "video/x-matroska"
    "mov" -> "video/quicktime"
    "3gp" -> "video/3gpp"
    "ts" -> "video/mp2t"
    "avi" -> "video/x-msvideo"
    "mp3" -> "audio/mpeg"
    "m4a" -> "audio/mp4"
    "aac" -> "audio/aac"
    "opus", "ogg" -> "audio/ogg"
    "wav" -> "audio/wav"
    "flac" -> "audio/flac"
    // Tasveerein — ye branches se PEHLE `else` me gir kar "video/mp4" ban jati thin.
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "heic", "heif" -> "image/heif"
    "bmp" -> "image/bmp"
    else -> if (audioOnly) "audio/mpeg" else "video/mp4"
}

/** Tree ke root me `name` folder dhoondo; na mile to banao. Return = uska document uri. */
private fun findOrCreateChildDir(context: Context, treeUri: Uri, name: String): Uri {
    val resolver = context.contentResolver
    val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
    val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
    try {
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ), null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name &&
                    c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                ) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0))
                }
            }
        }
    } catch (_: Exception) {
    }
    return DocumentsContract.createDocument(
        resolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, name
    ) ?: parentUri
}

/** Custom SAF folder me save (platform subfolder). Return = file ka content uri string. */
fun saveToCustomTree(context: Context, temp: File, platform: String, treeUri: Uri, audioOnly: Boolean, onCopy: (Long) -> Unit = {}): String {
    val resolver = context.contentResolver
    val dirUri = findOrCreateChildDir(context, treeUri, "XniperBuilds")
    // platform subfolder XniperBuilds ke andar
    val subUri = findOrCreateChildDirUnder(context, treeUri, dirUri, platform)
    val fileUri = DocumentsContract.createDocument(
        resolver, subUri, mimeForExt(temp.extension, audioOnly), temp.name
    ) ?: throw Exception("Couldn't create file in the custom folder")
    resolver.openOutputStream(fileUri)?.use { out ->
        temp.inputStream().use { input -> copyChunked(input, out, onCopy) }
    } ?: throw Exception("Output stream null")
    temp.delete()
    return fileUri.toString()
}

/** parentDocUri (jo tree ka koi subfolder hai) ke andar `name` folder dhoondo/banao. */
private fun findOrCreateChildDirUnder(context: Context, treeUri: Uri, parentDocUri: Uri, name: String): Uri {
    val resolver = context.contentResolver
    val parentDocId = DocumentsContract.getDocumentId(parentDocUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
    try {
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ), null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name &&
                    c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                ) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0))
                }
            }
        }
    } catch (_: Exception) {
    }
    return DocumentsContract.createDocument(
        resolver, parentDocUri, DocumentsContract.Document.MIME_TYPE_DIR, name
    ) ?: parentDocUri
}

/** SAF folder ka readable naam (display ke liye). */
fun treeDisplayName(context: Context, treeUri: Uri): String {
    return try {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        docId.substringAfterLast(':').ifBlank { docId }
    } catch (e: Exception) {
        treeUri.lastPathSegment ?: "folder"
    }
}

/** App ke downloads ka storage hisaab (Movies + Music/XniperBuilds) + temp cache. */
data class StorageInfo(
    val videoBytes: Long, val videoCount: Int,
    val audioBytes: Long, val audioCount: Int,
    val tempBytes: Long
) {
    val totalBytes: Long get() = videoBytes + audioBytes + tempBytes
}

private fun queryMediaSize(context: Context, contentUri: android.net.Uri): Pair<Long, Int> {
    var bytes = 0L
    var count = 0
    val proj = arrayOf(MediaStore.MediaColumns.SIZE)
    val sel = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
    val args = arrayOf("%XniperBuilds%")
    try {
        context.contentResolver.query(contentUri, proj, sel, args, null)?.use { c ->
            val sizeIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (c.moveToNext()) {
                bytes += c.getLong(sizeIdx)
                count++
            }
        }
    } catch (e: Exception) {
        // purani API / column missing — 0 rehne do
    }
    return bytes to count
}

fun storageUsage(context: Context): StorageInfo {
    val (vB, vC) = if (Build.VERSION.SDK_INT >= 29)
        queryMediaSize(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI) else 0L to 0
    val (aB, aC) = if (Build.VERSION.SDK_INT >= 29)
        queryMediaSize(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) else 0L to 0
    val temp = context.getExternalFilesDir("temp")
        ?.walkBottomUp()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
    return StorageInfo(vB, vC, aB, aC, temp)
}

/** Bytes ko readable string me (KB/MB/GB). Locale.US — har device pe same digits. */
fun humanBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val kb = b / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}
