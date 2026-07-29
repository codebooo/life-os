package com.lifeos.feature.pastebin.data

import com.lifeos.core.common.log.LifeLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.math.BigInteger
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.zip.Deflater
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Burner pastes via PrivateBin (§Module Pastebin).
 *
 * Pastebin's developer API has no burn-after-read and no paste password - those
 * only exist in its web UI - so anything that must self-destruct or be password
 * protected goes to a PrivateBin instance instead. PrivateBin is end-to-end
 * encrypted: the key never leaves the phone, it travels in the link fragment,
 * and the server only ever holds ciphertext.
 *
 * Format (PrivateBin v2): AES-256-GCM over raw-deflated JSON, key material is
 * PBKDF2-HMAC-SHA256 over (random key + password), and the parameter array is
 * also the AEAD associated data - so it is built as an exact string and reused
 * verbatim in the request.
 */
@Singleton
class PrivateBinClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val random = SecureRandom()

    /**
     * Creates an encrypted paste.
     *
     * @return the shareable URL, including the decryption key in the fragment.
     */
    suspend fun create(
        instance: String = DEFAULT_INSTANCE,
        text: String,
        expiry: PasteExpiry,
        burnAfterRead: Boolean,
        password: String,
        markdown: Boolean = false,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val key = ByteArray(32).also(random::nextBytes)
            val iv = ByteArray(16).also(random::nextBytes)
            val salt = ByteArray(8).also(random::nextBytes)

            val spec = buildString {
                append('[')
                append('"').append(b64(iv)).append('"').append(',')
                append('"').append(b64(salt)).append('"').append(',')
                append(ITERATIONS).append(',')
                append(KEY_BITS).append(',')
                append(TAG_BITS).append(',')
                append("\"aes\",\"gcm\",\"zlib\"")
                append(']')
            }
            val formatting = if (markdown) "markdown" else "plaintext"
            // Exactly the bytes the server will store, and exactly the AAD.
            val adata = "[$spec,\"$formatting\",0,${if (burnAfterRead) 1 else 0}]"

            val payload = JSONObject().put("paste", text).toString().toByteArray()
            val compressed = deflateRaw(payload)
            val derived = pbkdf2(key + password.toByteArray(), salt, ITERATIONS, KEY_BITS / 8)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(TAG_BITS, iv))
                updateAAD(adata.toByteArray())
            }
            val ct = cipher.doFinal(compressed)

            val body = """{"v":2,"adata":$adata,"ct":"${b64(ct)}","meta":{"expire":"${expiry.privateBinValue}"}}"""
            val base = instance.trim().trimEnd('/').ifBlank { DEFAULT_INSTANCE }
            val request = Request.Builder()
                .url("$base/")
                // PrivateBin only speaks its JSON API when asked to.
                .header("X-Requested-With", "JSONHttpRequest")
                .post(body.toRequestBody(JSON))
                .build()
            val response = client.newCall(request).execute().use { it.body?.string().orEmpty() }
            val json = runCatching { JSONObject(response) }
                .getOrElse { error("${hostOf(base)} did not answer with JSON") }
            if (json.optInt("status", 1) != 0) {
                error(json.optString("message").ifBlank { "PrivateBin rejected the paste" })
            }
            val id = json.optString("id").ifBlank { error("PrivateBin returned no paste id") }
            "$base/?$id#${base58(key)}"
        }.onFailure { LifeLogger.w(TAG, "PrivateBin create failed", it) }
    }

    // ---- crypto and encoding helpers ---------------------------------------

    /** PBKDF2-HMAC-SHA256 over raw bytes (PBEKeySpec would re-encode them). */
    private fun pbkdf2(material: ByteArray, salt: ByteArray, iterations: Int, lengthBytes: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(material, "HmacSHA256")) }
        val blockSize = mac.macLength
        val blocks = (lengthBytes + blockSize - 1) / blockSize
        val output = ByteArray(blocks * blockSize)
        var offset = 0
        for (block in 1..blocks) {
            var u = mac.doFinal(salt + byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
            val t = u.copyOf()
            repeat(iterations - 1) {
                u = mac.doFinal(u)
                for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
            }
            t.copyInto(output, offset)
            offset += blockSize
        }
        return output.copyOf(lengthBytes)
    }

    /** Raw deflate (no zlib header), which is what PrivateBin's "zlib" means. */
    private fun deflateRaw(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        return try {
            deflater.setInput(data)
            deflater.finish()
            val buffer = ByteArray(4096)
            val out = java.io.ByteArrayOutputStream()
            while (!deflater.finished()) {
                val n = deflater.deflate(buffer)
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    /** Bitcoin-style base58, the alphabet PrivateBin uses for the key fragment. */
    private fun base58(bytes: ByteArray): String {
        var value = BigInteger(1, bytes)
        val fifty8 = BigInteger.valueOf(58)
        val builder = StringBuilder()
        while (value > BigInteger.ZERO) {
            val (quotient, remainder) = value.divideAndRemainder(fifty8)
            builder.append(ALPHABET[remainder.toInt()])
            value = quotient
        }
        bytes.takeWhile { it == 0.toByte() }.forEach { _ -> builder.append(ALPHABET[0]) }
        return builder.reverse().toString()
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault(url)

    companion object {
        /** Default public instance; changeable in the module's settings tab. */
        const val DEFAULT_INSTANCE = "https://privatebin.net"
        private const val TAG = "PrivateBinClient"
        private const val ITERATIONS = 100_000
        private const val KEY_BITS = 256
        private const val TAG_BITS = 128
        private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        private val JSON = "application/json".toMediaType()
    }
}
