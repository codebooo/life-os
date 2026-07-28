package com.lifeos.feature.brick.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable

/** MIME type of the record Brick writes, matched by the manifest NDEF filter. */
const val BRICK_MIME = "application/vnd.lifeos.brick"

/**
 * Programs a tag so taps work with LifeOS closed (§Module Brick).
 *
 * Reading a tag's serial number only works while an app holds NFC reader mode,
 * i.e. while LifeOS is open. For a tap to reach a closed app, Android needs the
 * tag itself to carry a record the system can route: a MIME record LifeOS
 * declares an intent filter for, plus an Android Application Record so LifeOS
 * always wins the dispatch instead of a chooser appearing.
 */
object BrickTagWriter {

    /** Writes the Brick record, keeping the tag's serial number as the payload. */
    fun write(tag: Tag, uid: String, packageName: String): Result<Unit> = runCatching {
        val message = NdefMessage(
            arrayOf(
                NdefRecord.createMime(BRICK_MIME, uid.uppercase().toByteArray()),
                // Last record: the system launches this package and nothing else.
                NdefRecord.createApplicationRecord(packageName),
            ),
        )

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect()
            try {
                require(ndef.isWritable) { "That tag is write-protected" }
                require(ndef.maxSize >= message.toByteArray().size) {
                    "That tag is too small (${ndef.maxSize} bytes)"
                }
                ndef.writeNdefMessage(message)
            } finally {
                runCatching { ndef.close() }
            }
            return@runCatching
        }

        val formatable = NdefFormatable.get(tag)
            ?: error("That tag cannot store data, so taps only work with LifeOS open")
        formatable.connect()
        try {
            formatable.format(message)
        } finally {
            runCatching { formatable.close() }
        }
    }

    /** Reads the UID Brick wrote onto the tag, if any (used by intent dispatch). */
    fun brickPayload(message: NdefMessage?): String? = message?.records
        ?.firstOrNull { record ->
            String(record.type).equals(BRICK_MIME, ignoreCase = true)
        }
        ?.let { String(it.payload).trim().uppercase() }
        ?.takeIf { it.isNotEmpty() }
}
