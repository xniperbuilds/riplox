package com.xniperbuilds.downloader

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Export/import ka ek link. */
data class PackLink(val url: String, val title: String, val isAudio: Boolean)

/**
 * "Riplox link pack" — history ke links ek file me (naye phone pe import → select → download).
 * Password optional: diya to AES-256-GCM (PBKDF2, 100k) encrypt — bina password file khulti nahi.
 */
object LinkPack {
    private const val MAGIC = "RIPLOXPACK1"
    private const val MAGIC_ENC = "RIPLOXENC1:"

    class NeedPassword : Exception()
    class BadPassword : Exception()

    fun export(links: List<PackLink>, password: String?): String {
        val arr = JSONArray()
        links.forEach { l ->
            arr.put(JSONObject().apply {
                put("u", l.url); put("t", l.title); put("a", l.isAudio)
            })
        }
        val plain = JSONObject().apply {
            put("magic", MAGIC)
            put("app", "Riplox")
            put("links", arr)
        }.toString()
        if (password.isNullOrBlank()) return plain

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val blob = salt + iv + ct
        return MAGIC_ENC + Base64.encodeToString(blob, Base64.NO_WRAP)
    }

    /** throws NeedPassword (encrypted + password null) / BadPassword (galat password). */
    fun parse(text: String, password: String?): List<PackLink> {
        val t = text.trim()
        val plain: String = if (t.startsWith(MAGIC_ENC)) {
            if (password.isNullOrBlank()) throw NeedPassword()
            try {
                val blob = Base64.decode(t.removePrefix(MAGIC_ENC), Base64.NO_WRAP)
                val salt = blob.copyOfRange(0, 16)
                val iv = blob.copyOfRange(16, 28)
                val ct = blob.copyOfRange(28, blob.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
                String(cipher.doFinal(ct), Charsets.UTF_8)
            } catch (e: Exception) {
                throw BadPassword()
            }
        } else t
        val o = JSONObject(plain)
        if (o.optString("magic") != MAGIC) throw IllegalArgumentException("Not a Riplox link file")
        val arr = o.getJSONArray("links")
        return List(arr.length()) { i ->
            val l = arr.getJSONObject(i)
            PackLink(l.getString("u"), l.optString("t", l.getString("u")), l.optBoolean("a", false))
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, 100_000, 256)
        val kf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(kf.generateSecret(spec).encoded, "AES")
    }
}
