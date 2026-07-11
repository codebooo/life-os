package com.lifeos.core.common.storage

import android.os.Environment
import com.lifeos.core.common.log.LifeLogger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors module data into a user-visible `/LifeOS` folder on shared internal
 * storage (§Module storage). Requires All-files access (MANAGE_EXTERNAL_STORAGE)
 * — without it, every call is a silent no-op, so callers never need to guard.
 *
 * Layout: `/storage/emulated/0/LifeOS/<Module>/…`, e.g. `LifeOS/Notes/foo.md`.
 */
@Singleton
class LifeOsPublicMirror @Inject constructor() {

    fun isAvailable(): Boolean =
        Environment.isExternalStorageManager()

    private fun moduleDir(module: String): File? {
        if (!isAvailable()) return null
        val base = File(Environment.getExternalStorageDirectory(), "LifeOS/$module")
        return if (base.exists() || base.mkdirs()) base else null
    }

    /** Writes/updates a text file (e.g. a note) in the module's folder. */
    fun writeText(module: String, fileName: String, content: String) {
        val dir = moduleDir(module) ?: return
        runCatching { File(dir, fileName).writeText(content) }
            .onFailure { LifeLogger.w(TAG, "Mirror write failed for $module/$fileName", it) }
    }

    /** Removes a mirrored file if present. */
    fun delete(module: String, fileName: String) {
        val dir = moduleDir(module) ?: return
        runCatching { File(dir, fileName).delete() }
    }

    private companion object {
        const val TAG = "LifeOsPublicMirror"
    }
}
