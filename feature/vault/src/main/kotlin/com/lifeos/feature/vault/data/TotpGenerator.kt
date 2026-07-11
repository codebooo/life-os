package com.lifeos.feature.vault.data

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 6238 time-based one-time passwords, on-device (§Module Vault). Same
 * algorithm every authenticator app uses: HMAC-SHA1 over a 30-second counter,
 * truncated to 6 digits. The Base32 secret is what a site shows as its "2FA
 * key". No network, no third-party library.
 */
object TotpGenerator {

    private const val PERIOD_SECONDS = 30L
    private const val DIGITS = 6

    /** Current code for [base32Secret], or null if the secret can't be decoded. */
    fun code(base32Secret: String, nowMillis: Long): String? {
        val key = decodeBase32(base32Secret.trim().replace(" ", "").uppercase()) ?: return null
        if (key.isEmpty()) return null
        val counter = nowMillis / 1000 / PERIOD_SECONDS
        val msg = ByteArray(8)
        var value = counter
        for (i in 7 downTo 0) {
            msg[i] = (value and 0xff).toByte()
            value = value shr 8
        }
        return runCatching {
            val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(key, "HmacSHA1")) }
            val hash = mac.doFinal(msg)
            val offset = (hash[hash.size - 1].toInt() and 0x0f)
            val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)
            val otp = binary % 1_000_000
            otp.toString().padStart(DIGITS, '0')
        }.getOrNull()
    }

    /** Seconds remaining in the current 30s window (for the countdown ring). */
    fun secondsRemaining(nowMillis: Long): Int =
        (PERIOD_SECONDS - (nowMillis / 1000) % PERIOD_SECONDS).toInt()

    private fun decodeBase32(input: String): ByteArray? {
        if (input.isBlank()) return null
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val clean = input.trimEnd('=')
        var buffer = 0
        var bitsLeft = 0
        val output = ArrayList<Byte>(clean.length * 5 / 8)
        for (c in clean) {
            val idx = alphabet.indexOf(c)
            if (idx < 0) return null
            buffer = (buffer shl 5) or idx
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output.add(((buffer shr bitsLeft) and 0xff).toByte())
            }
        }
        return output.toByteArray()
    }
}

/** A cryptographically-varied password generator (§Module Vault). */
object PasswordGenerator {

    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{}"

    fun generate(
        length: Int = 20,
        useUpper: Boolean = true,
        useDigits: Boolean = true,
        useSymbols: Boolean = true,
    ): String {
        val random = java.security.SecureRandom()
        val pool = buildString {
            append(LOWER)
            if (useUpper) append(UPPER)
            if (useDigits) append(DIGITS)
            if (useSymbols) append(SYMBOLS)
        }
        return (0 until length.coerceIn(8, 64))
            .map { pool[random.nextInt(pool.length)] }
            .joinToString("")
    }
}
