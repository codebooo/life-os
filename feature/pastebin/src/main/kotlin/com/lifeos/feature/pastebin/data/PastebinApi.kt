package com.lifeos.feature.pastebin.data

import com.lifeos.core.common.log.LifeLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** How long a paste lives (Pastebin's `api_paste_expire_date` values). */
enum class PasteExpiry(val apiValue: String, val label: String) {
    NEVER("N", "Never"),
    TEN_MINUTES("10M", "10 minutes"),
    ONE_HOUR("1H", "1 hour"),
    ONE_DAY("1D", "1 day"),
    ONE_WEEK("1W", "1 week"),
    TWO_WEEKS("2W", "2 weeks"),
    ONE_MONTH("1M", "1 month"),
    SIX_MONTHS("6M", "6 months"),
    ONE_YEAR("1Y", "1 year"),
}

/** Who can see a paste (`api_paste_private`). */
enum class PasteVisibility(val apiValue: Int, val label: String) {
    PUBLIC(0, "Public"),
    UNLISTED(1, "Unlisted"),
    PRIVATE(2, "Private (needs your account)"),
}

/** Everything the composer can set on a new paste. */
data class PasteRequest(
    val title: String,
    val content: String,
    val expiry: PasteExpiry = PasteExpiry.NEVER,
    val visibility: PasteVisibility = PasteVisibility.UNLISTED,
    /** Pastebin's syntax id, e.g. "text", "kotlin", "json". */
    val format: String = "text",
    /** Burn-after-read. Pastebin only honours this for guest (non-logged-in) pastes. */
    val burnAfterRead: Boolean = false,
    /** Optional password prompt before the paste can be read. */
    val password: String = "",
)

/** A paste listed from the account. */
data class PasteSummary(
    val key: String,
    val title: String,
    val url: String,
    val createdAt: Long,
    val expiresLabel: String,
    val visibility: String,
    val size: Long,
    val hits: Long,
)

/**
 * Pastebin's classic developer API (§Module Pastebin). Endpoints are plain
 * form-POSTs returning either a URL or `Bad API request, …`, so the client's
 * job is mostly translating that into a Result. Listing, reading and deleting
 * additionally need a user key, which we mint from a username/password once and
 * keep in settings.
 */
@Singleton
class PastebinApi @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Creates a paste; returns its URL. [userKey] posts it under the account. */
    suspend fun createPaste(
        devKey: String,
        request: PasteRequest,
        userKey: String? = null,
    ): Result<String> = post(
        url = POST_URL,
        fields = buildMap {
            put("api_dev_key", devKey)
            put("api_option", "paste")
            put("api_paste_code", request.content)
            put("api_paste_name", request.title)
            put("api_paste_expire_date", request.expiry.apiValue)
            put("api_paste_private", request.visibility.apiValue.toString())
            put("api_paste_format", request.format)
            if (request.burnAfterRead) put("api_paste_burn", "1")
            if (request.password.isNotBlank()) put("api_paste_password", request.password)
            if (!userKey.isNullOrBlank()) put("api_user_key", userKey)
        },
    ).map { it.trim() }

    /** Exchanges account credentials for the user key needed by list/delete. */
    suspend fun login(devKey: String, username: String, password: String): Result<String> = post(
        url = LOGIN_URL,
        fields = mapOf(
            "api_dev_key" to devKey,
            "api_user_name" to username,
            "api_user_password" to password,
        ),
    ).map { it.trim() }

    /** The account's pastes, newest first. */
    suspend fun listPastes(devKey: String, userKey: String, limit: Int = 50): Result<List<PasteSummary>> =
        post(
            url = POST_URL,
            fields = mapOf(
                "api_dev_key" to devKey,
                "api_user_key" to userKey,
                "api_option" to "list",
                "api_results_limit" to limit.coerceIn(1, 1000).toString(),
            ),
        ).map { parsePasteList(it) }

    suspend fun deletePaste(devKey: String, userKey: String, pasteKey: String): Result<Unit> =
        post(
            url = POST_URL,
            fields = mapOf(
                "api_dev_key" to devKey,
                "api_user_key" to userKey,
                "api_option" to "delete",
                "api_paste_key" to pasteKey,
            ),
        ).map { }

    /** Raw text of one of the account's own pastes. */
    suspend fun readPaste(devKey: String, userKey: String, pasteKey: String): Result<String> =
        post(
            url = RAW_URL,
            fields = mapOf(
                "api_dev_key" to devKey,
                "api_user_key" to userKey,
                "api_option" to "show_paste",
                "api_paste_key" to pasteKey,
            ),
        )

    private suspend fun post(url: String, fields: Map<String, String>): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = FormBody.Builder().apply {
                    fields.forEach { (key, value) -> add(key, value) }
                }.build()
                client.newCall(Request.Builder().url(url).post(body).build()).execute().use { response ->
                    val text = response.body.string().trim()
                    // The API answers 200 with a "Bad API request, …" body on failure.
                    if (!response.isSuccessful) error("HTTP ${response.code}: ${text.take(160)}")
                    if (text.startsWith("Bad API request")) {
                        error(text.removePrefix("Bad API request,").trim().ifBlank { text })
                    }
                    text
                }
            }.onFailure { LifeLogger.w(TAG, "Pastebin call failed", it) }
        }

    /** The `list` option returns a stream of <paste> XML blocks. */
    private fun parsePasteList(xml: String): List<PasteSummary> =
        PASTE_BLOCK.findAll(xml).map { block ->
            val text = block.value
            fun field(name: String) = Regex("<paste_$name>(.*?)</paste_$name>", RegexOption.DOT_MATCHES_ALL)
                .find(text)?.groupValues?.get(1)?.trim().orEmpty()
            val expireStamp = field("expire_date").toLongOrNull() ?: 0L
            PasteSummary(
                key = field("key"),
                title = field("title").ifBlank { "Untitled" },
                url = field("url"),
                createdAt = (field("date").toLongOrNull() ?: 0L) * 1000L,
                expiresLabel = if (expireStamp == 0L) "Never" else "Expires",
                visibility = when (field("private")) {
                    "0" -> "Public"
                    "2" -> "Private"
                    else -> "Unlisted"
                },
                size = field("size").toLongOrNull() ?: 0L,
                hits = field("hits").toLongOrNull() ?: 0L,
            )
        }.toList()

    companion object {
        /** Personal developer key for this sideloaded build (§Module Pastebin). */
        const val DEFAULT_DEV_KEY = "RMdq0LvnD38fn3jDoLtnE4k7zSlu6FTJ"

        /** Syntax ids offered in the composer; Pastebin supports many more. */
        val FORMATS = listOf(
            "text", "kotlin", "java", "javascript", "typescript", "python", "bash",
            "json", "yaml", "xml", "html5", "css", "sql", "markdown", "rust", "go", "c", "cpp",
        )

        private const val POST_URL = "https://pastebin.com/api/api_post.php"
        private const val LOGIN_URL = "https://pastebin.com/api/api_login.php"
        private const val RAW_URL = "https://pastebin.com/api/api_raw.php"
        private const val TAG = "PastebinApi"
        private val PASTE_BLOCK = Regex("<paste>(.*?)</paste>", RegexOption.DOT_MATCHES_ALL)
    }
}
