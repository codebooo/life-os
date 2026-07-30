package com.lifeos.feature.sync.data

import android.content.Context
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.core.database.LifeDatabase
import com.lifeos.core.database.backup.BackupDao
import com.lifeos.core.database.backup.BackupRunEntity
import com.lifeos.core.datastore.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** Where a snapshot ended up and whether it read back intact. */
data class BackupOutcome(
    val fileName: String,
    val sizeBytes: Long,
    val destination: String,
    val verified: Boolean,
    val detail: String = "",
)

/**
 * Encrypted database snapshots (§Module Sync).
 *
 * Everything in LifeOS lives in one sideloaded app on one phone, so the whole
 * point here is that a lost phone is not a lost life. A snapshot is a consistent
 * SQLite copy (`VACUUM INTO`, not a file copy of a live database), encrypted with
 * AES-256-GCM from a passphrase, written to the LifeOS folder and optionally
 * pushed to a WebDAV share on the NAS. Every run is verified by reading the
 * result back and decrypting it - an unverified backup is not a backup.
 */
@Singleton
class BackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: LifeDatabase,
    private val backupDao: BackupDao,
    private val settingsRepository: SettingsRepository,
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val random = SecureRandom()

    /** Runs a backup end to end and records the attempt either way. */
    suspend fun backupNow(): Result<BackupOutcome> = withContext(Dispatchers.IO) {
        val config = config()
        if (config.passphrase.isBlank()) {
            record("local", "", 0, "FAILED", "no passphrase set")
            return@withContext Result.failure(IllegalStateException("Set a backup passphrase first"))
        }
        runCatching {
            val snapshot = snapshot()
            try {
                val encrypted = encrypt(snapshot.readBytes(), config.passphrase)
                val name = "lifeos-${STAMP.format(Date())}.lifeosbak"
                val localDir = File(context.getExternalFilesDir("backups"), "").apply { mkdirs() }
                val localFile = File(localDir, name)
                localFile.writeBytes(encrypted)
                trimLocal(localDir, config.keepGenerations)

                var destination = localFile.absolutePath
                if (config.webdavUrl.isNotBlank()) {
                    upload(config, name, encrypted)
                    destination = "${config.webdavUrl.trimEnd('/')}/$name"
                }

                // Verification is the whole reason this exists: decrypt what was
                // written and check it is really a SQLite file.
                val readBack = decrypt(localFile.readBytes(), config.passphrase)
                val verified = readBack.size > 100 && String(readBack.copyOf(15)) == "SQLite format 3"
                record(
                    destination = destination,
                    fileName = name,
                    size = encrypted.size.toLong(),
                    status = if (verified) "VERIFIED" else "OK",
                    detail = if (verified) "" else "written but could not be verified",
                )
                BackupOutcome(name, encrypted.size.toLong(), destination, verified)
            } finally {
                snapshot.delete()
            }
        }.onFailure { failure ->
            LifeLogger.w(TAG, "Backup failed", failure)
            record("local", "", 0, "FAILED", failure.message.orEmpty())
        }
    }

    /**
     * Decrypts a snapshot and stages it next to the live database.
     *
     * Room holds the database open, so the swap itself happens on the next
     * process start: the staged file is applied by [applyStagedRestore] before
     * the database is opened, which is the only safe moment.
     */
    suspend fun restoreFrom(file: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val passphrase = config().passphrase
            require(passphrase.isNotBlank()) { "Set the passphrase the backup was made with" }
            val plain = decrypt(file.readBytes(), passphrase)
            require(String(plain.copyOf(15)) == "SQLite format 3") {
                "That file did not decrypt to a database - wrong passphrase?"
            }
            stagedFile().writeBytes(plain)
            "Restore staged: ${file.name}. Close and reopen LifeOS to apply it."
        }
    }

    /** Local snapshots available to restore from, newest first. */
    fun localBackups(): List<File> =
        File(context.getExternalFilesDir("backups"), "").listFiles()
            ?.filter { it.isFile && it.name.endsWith(".lifeosbak") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    suspend fun latestRun(): BackupRunEntity? = backupDao.latest()

    suspend fun history(limit: Int = 10): List<BackupRunEntity> = backupDao.recent(limit)

    // ---- snapshot and crypto ------------------------------------------------

    /** Consistent copy of the live database; VACUUM INTO is WAL-safe. */
    private fun snapshot(): File {
        val target = File(context.cacheDir, "lifeos-snapshot.db")
        target.delete()
        database.openHelper.writableDatabase.query("VACUUM INTO ?", arrayOf(target.absolutePath)).use { it.moveToFirst() }
        return target
    }

    private fun encrypt(plain: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        }
        val body = cipher.doFinal(plain)
        // Header keeps the format self-describing: magic, salt, iv, ciphertext.
        return MAGIC + salt + iv + body
    }

    private fun decrypt(blob: ByteArray, passphrase: String): ByteArray {
        require(blob.size > MAGIC.size + 28) { "That file is too small to be a LifeOS backup" }
        require(blob.copyOf(MAGIC.size).contentEquals(MAGIC)) { "That file is not a LifeOS backup" }
        val salt = blob.copyOfRange(MAGIC.size, MAGIC.size + 16)
        val iv = blob.copyOfRange(MAGIC.size + 16, MAGIC.size + 28)
        val body = blob.copyOfRange(MAGIC.size + 28, blob.size)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        }
        return cipher.doFinal(body)
    }

    /** PBKDF2-HMAC-SHA256 over raw bytes, 120k iterations. */
    private fun deriveKey(passphrase: String, salt: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(passphrase.toByteArray(), "HmacSHA256"))
        }
        var u = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
        val result = u.copyOf()
        repeat(ITERATIONS - 1) {
            u = mac.doFinal(u)
            for (i in result.indices) result[i] = (result[i].toInt() xor u[i].toInt()).toByte()
        }
        return result
    }

    private fun upload(config: BackupConfig, name: String, bytes: ByteArray) {
        val url = "${config.webdavUrl.trimEnd('/')}/$name"
        val builder = Request.Builder()
            .url(url)
            .put(bytes.toRequestBody(OCTET))
        if (config.webdavUser.isNotBlank()) {
            builder.header("Authorization", Credentials.basic(config.webdavUser, config.webdavPassword))
        }
        client.newCall(builder.build()).execute().use { response ->
            check(response.isSuccessful) { "WebDAV said HTTP ${response.code}" }
        }
    }

    private fun trimLocal(dir: File, keep: Int) {
        dir.listFiles()
            ?.filter { it.name.endsWith(".lifeosbak") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(keep.coerceAtLeast(1))
            ?.forEach { it.delete() }
    }

    private suspend fun record(destination: String, fileName: String, size: Long, status: String, detail: String) {
        backupDao.insert(
            BackupRunEntity(
                at = System.currentTimeMillis(),
                destination = destination,
                fileName = fileName,
                sizeBytes = size,
                status = status,
                detail = detail.take(160),
            ),
        )
    }

    private suspend fun config(): BackupConfig = BackupConfig(
        passphrase = settingsRepository.backupPassphrase.first(),
        webdavUrl = settingsRepository.backupWebdavUrl.first(),
        webdavUser = settingsRepository.backupWebdavUser.first(),
        webdavPassword = settingsRepository.backupWebdavPassword.first(),
        keepGenerations = settingsRepository.backupKeepGenerations.first(),
    )

    companion object {
        private const val TAG = "BackupService"
        private const val ITERATIONS = 120_000
        private val MAGIC = "LIFEOSBAK1".toByteArray()
        private val OCTET = "application/octet-stream".toMediaType()
        private val STAMP = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)

        /** Where a decrypted snapshot waits for the next cold start. */
        fun stagedFile(context: Context): File = File(context.filesDir, "restore-staged.db")

        /**
         * Applies a staged restore before Room opens the database. Called from
         * the application's onCreate, which is the only point where swapping the
         * file underneath Room is safe.
         */
        fun applyStagedRestore(context: Context, databaseName: String): Boolean {
            val staged = stagedFile(context)
            if (!staged.exists()) return false
            val target = context.getDatabasePath(databaseName)
            return runCatching {
                target.parentFile?.mkdirs()
                // Drop the WAL and shm so SQLite cannot mix old journal with new data.
                File(target.parentFile, "${target.name}-wal").delete()
                File(target.parentFile, "${target.name}-shm").delete()
                staged.copyTo(target, overwrite = true)
                staged.delete()
                LifeLogger.i(TAG, "Restored database from staged snapshot")
                true
            }.getOrElse {
                LifeLogger.e(TAG, "Staged restore failed", it)
                false
            }
        }
    }

    private fun stagedFile(): File = stagedFile(context)
}

private data class BackupConfig(
    val passphrase: String,
    val webdavUrl: String,
    val webdavUser: String,
    val webdavPassword: String,
    val keepGenerations: Int,
)
