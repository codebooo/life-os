package com.lifeos.feature.brick.nfc

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag

/** Hex UID of a scanned tag, uppercase — the id Brick pairs modes against. */
fun Tag.uid(): String? =
    id?.joinToString("") { "%02X".format(it) }?.takeIf { it.isNotEmpty() }

/** Hex UID from an NFC dispatch intent, or null when it carries no tag. */
fun Intent.tagUid(): String? {
    @Suppress("DEPRECATION")
    val tag = getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return null
    return tag.uid()
}

/**
 * Reader-mode helper (§Module Brick). Foreground reader mode beats the system's
 * intent dispatch, so a tap is delivered to whatever screen is open instead of
 * bouncing through a new activity — that is what makes in-app taps work.
 */
object BrickReader {

    private const val FLAGS = NfcAdapter.FLAG_READER_NFC_A or
        NfcAdapter.FLAG_READER_NFC_B or
        NfcAdapter.FLAG_READER_NFC_F or
        NfcAdapter.FLAG_READER_NFC_V or
        NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS or
        NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

    /** Starts reading; returns false when the device has no usable NFC. */
    fun start(activity: Activity, onUid: (String) -> Boolean): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return false
        if (!adapter.isEnabled) return false
        runCatching {
            adapter.enableReaderMode(
                activity,
                { tag -> tag.uid()?.let { onUid(it) } },
                FLAGS,
                null,
            )
        }.onFailure { return false }
        return true
    }

    fun stop(activity: Activity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        runCatching { adapter.disableReaderMode(activity) }
    }

    fun isAvailable(activity: Activity): Boolean =
        NfcAdapter.getDefaultAdapter(activity)?.isEnabled == true
}
