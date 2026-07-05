package com.lifeos.feature.email.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lifeos.core.common.coroutines.DispatcherProvider
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.result.runCatchingLife
import com.lifeos.core.database.email.EmailDao
import com.lifeos.core.database.email.EmailMessageEntity
import com.lifeos.core.service.LifeEvent
import com.lifeos.core.service.LifeEventBus
import jakarta.mail.Folder
import jakarta.mail.Session
import jakarta.mail.internet.MimeMultipart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

data class ImapConfig(val host: String, val port: Int, val user: String, val password: String)

/**
 * Direct IMAP against the NAS Proton Bridge (§Module 1 fallback path; the
 * protonmail-pro-mcp primary path joins once the NAS MCP endpoint is up,
 * §8.5). Bridge app password only — never the account password (§9.3).
 */
interface EmailRepository {
    fun observeEmails(): Flow<List<EmailMessageEntity>>
    val config: Flow<ImapConfig>
    suspend fun saveConfig(config: ImapConfig)
    suspend fun refresh(): LifeResult<Int>
    suspend fun delete(id: Long)
}

@Singleton
internal class DefaultEmailRepository @Inject constructor(
    private val emailDao: EmailDao,
    private val dataStore: DataStore<Preferences>,
    private val eventBus: LifeEventBus,
    private val dispatchers: DispatcherProvider,
) : EmailRepository {

    override fun observeEmails(): Flow<List<EmailMessageEntity>> = emailDao.observeAll()

    override val config: Flow<ImapConfig> = dataStore.data.map { prefs ->
        ImapConfig(
            host = prefs[KEY_HOST] ?: "",
            port = (prefs[KEY_PORT] ?: "1143").toIntOrNull() ?: 1143,
            user = prefs[KEY_USER] ?: "",
            password = prefs[KEY_PASSWORD] ?: "",
        )
    }

    override suspend fun saveConfig(config: ImapConfig) {
        dataStore.edit {
            it[KEY_HOST] = config.host.trim()
            it[KEY_PORT] = config.port.toString()
            it[KEY_USER] = config.user.trim()
            it[KEY_PASSWORD] = config.password
        }
    }

    override suspend fun refresh(): LifeResult<Int> = withContext(dispatchers.io) {
        runCatchingLife {
            val cfg = config.first()
            check(cfg.host.isNotBlank() && cfg.user.isNotBlank()) {
                "IMAP not configured — set Bridge host/user/app-password in Email settings"
            }
            val props = Properties().apply {
                put("mail.store.protocol", "imap")
                put("mail.imap.host", cfg.host)
                put("mail.imap.port", cfg.port.toString())
                put("mail.imap.ssl.enable", (cfg.port == 993).toString())
                put("mail.imap.connectiontimeout", "8000")
                put("mail.imap.timeout", "15000")
            }
            val session = Session.getInstance(props)
            val store = session.getStore("imap")
            var stored = 0
            store.connect(cfg.host, cfg.port, cfg.user, cfg.password)
            store.use {
                val inbox = it.getFolder("INBOX")
                inbox.open(Folder.READ_ONLY)
                val total = inbox.messageCount
                val from = maxOf(1, total - 29)
                inbox.getMessages(from, total).reversed().forEach { message ->
                    val subject = message.subject.orEmpty()
                    val sender = message.from?.firstOrNull()?.toString().orEmpty()
                    val body = bodyText(message.content)
                    val invite = EmailSignals.parseInvite(body)
                    val entity = EmailMessageEntity(
                        messageUid = "${message.receivedDate?.time ?: 0}-${subject.hashCode()}-${sender.hashCode()}",
                        from = sender,
                        subject = subject,
                        preview = body.take(300),
                        receivedAt = message.receivedDate?.time ?: System.currentTimeMillis(),
                        hasInvoiceSignal = EmailSignals.hasInvoiceSignal(subject, body),
                        hasInviteSignal = invite != null,
                        hasSubscriptionSignal = EmailSignals.hasSubscriptionSignal(subject, body),
                    )
                    val id = emailDao.insert(entity)
                    if (id > 0) {
                        stored++
                        eventBus.tryPublish(
                            LifeEvent.EmailReceived(
                                emailId = id,
                                from = sender,
                                subject = subject,
                                hasInvoiceSignal = entity.hasInvoiceSignal,
                                inviteStartsAt = invite?.startsAt,
                                inviteTitle = invite?.title,
                            ),
                        )
                    }
                }
                inbox.close(false)
            }
            stored
        }
    }

    override suspend fun delete(id: Long) = withContext(dispatchers.io) { emailDao.delete(id) }

    private fun bodyText(content: Any?): String = when (content) {
        is String -> content
        is MimeMultipart -> buildString {
            for (i in 0 until content.count) {
                append(bodyText(content.getBodyPart(i).content))
                append('\n')
            }
        }
        else -> ""
    }

    private companion object {
        val KEY_HOST = stringPreferencesKey("email_imap_host")
        val KEY_PORT = stringPreferencesKey("email_imap_port")
        val KEY_USER = stringPreferencesKey("email_imap_user")
        val KEY_PASSWORD = stringPreferencesKey("email_imap_password")
    }
}
