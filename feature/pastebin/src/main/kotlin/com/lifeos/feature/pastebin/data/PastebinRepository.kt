package com.lifeos.feature.pastebin.data

import com.lifeos.core.datastore.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Settings applied to pastes created from the Android share sheet. */
data class ShareDefaults(
    val expiry: PasteExpiry = PasteExpiry.ONE_WEEK,
    val visibility: PasteVisibility = PasteVisibility.UNLISTED,
    val burnAfterRead: Boolean = false,
    val password: String = "",
) {
    fun encode(): String = listOf(
        expiry.name,
        visibility.name,
        if (burnAfterRead) "1" else "0",
        password,
    ).joinToString("|")

    companion object {
        fun decode(raw: String): ShareDefaults {
            if (raw.isBlank()) return ShareDefaults()
            val parts = raw.split('|')
            return ShareDefaults(
                expiry = parts.getOrNull(0)
                    ?.let { name -> PasteExpiry.entries.firstOrNull { it.name == name } } ?: PasteExpiry.ONE_WEEK,
                visibility = parts.getOrNull(1)
                    ?.let { name -> PasteVisibility.entries.firstOrNull { it.name == name } } ?: PasteVisibility.UNLISTED,
                burnAfterRead = parts.getOrNull(2) == "1",
                password = parts.drop(3).joinToString("|"),
            )
        }
    }
}

/**
 * Pastebin (§Module Pastebin): wraps the API with the stored dev key, the
 * account user key and the share-sheet defaults, so callers (the module UI and
 * the share overlay) never juggle credentials themselves.
 */
@Singleton
class PastebinRepository @Inject constructor(
    private val api: PastebinApi,
    private val settingsRepository: SettingsRepository,
) {

    private val devKey get() = PastebinApi.DEFAULT_DEV_KEY

    suspend fun shareDefaults(): ShareDefaults =
        ShareDefaults.decode(settingsRepository.pastebinShareDefaults.first())

    suspend fun setShareDefaults(defaults: ShareDefaults) {
        settingsRepository.setPastebinShareDefaults(defaults.encode())
    }

    suspend fun userKey(): String = settingsRepository.pastebinUserKey.first()

    suspend fun signIn(username: String, password: String): Result<Unit> =
        api.login(devKey, username, password).mapCatching { key ->
            settingsRepository.setPastebinUserKey(key)
        }

    suspend fun signOut() = settingsRepository.setPastebinUserKey("")

    suspend fun create(request: PasteRequest, underAccount: Boolean): Result<String> {
        val key = if (underAccount) userKey().ifBlank { null } else null
        return api.createPaste(devKey, request, key)
    }

    /** Creates a paste using the saved share-sheet defaults. */
    suspend fun createFromShare(title: String, content: String): Result<String> {
        val defaults = shareDefaults()
        return create(
            PasteRequest(
                title = title,
                content = content,
                expiry = defaults.expiry,
                visibility = defaults.visibility,
                burnAfterRead = defaults.burnAfterRead,
                password = defaults.password,
            ),
            // Burn-after-read only works for guest pastes, so a burner paste is
            // deliberately posted without the account key.
            underAccount = !defaults.burnAfterRead,
        )
    }

    suspend fun list(): Result<List<PasteSummary>> {
        val key = userKey()
        if (key.isBlank()) return Result.failure(IllegalStateException("Sign in to list your pastes"))
        return api.listPastes(devKey, key)
    }

    suspend fun delete(pasteKey: String): Result<Unit> {
        val key = userKey()
        if (key.isBlank()) return Result.failure(IllegalStateException("Sign in to delete pastes"))
        return api.deletePaste(devKey, key, pasteKey)
    }

    suspend fun read(pasteKey: String): Result<String> {
        val key = userKey()
        if (key.isBlank()) return Result.failure(IllegalStateException("Sign in to read your pastes"))
        return api.readPaste(devKey, key, pasteKey)
    }
}
