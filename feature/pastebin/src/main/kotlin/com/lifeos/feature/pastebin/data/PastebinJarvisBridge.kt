package com.lifeos.feature.pastebin.data

import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.service.ActionEcho
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionHandler
import com.lifeos.core.service.LifeDataProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/** Pastebin as Jarvis reads it: the account's pastes and the share defaults. */
internal class PastebinProvider @Inject constructor(
    private val repository: PastebinRepository,
) : LifeDataProvider {

    override val topic: String = "pastes"
    override val description: String = "your Pastebin pastes and the burner defaults"

    override suspend fun read(query: String?): String {
        val defaults = repository.shareDefaults()
        val signedIn = repository.userKey().isNotBlank()
        val list = if (signedIn) repository.list().getOrNull().orEmpty() else emptyList()
        return buildString {
            appendLine("Pastebin: ${if (signedIn) "signed in" else "not signed in (guest pastes only)"}")
            appendLine(
                "Share defaults: expiry ${defaults.expiry.label}, ${defaults.visibility.label}" +
                    (if (defaults.burnAfterRead) ", burn after read" else "") +
                    (if (defaults.password.isNotBlank()) ", password set" else ""),
            )
            if (list.isEmpty()) {
                appendLine("No pastes listed.")
            } else {
                appendLine("Recent pastes:")
                list.take(8).forEach { paste ->
                    appendLine("- ${paste.title} (${paste.visibility}, ${paste.hits} hits) ${paste.url}")
                }
            }
        }.trim()
    }
}

/** Creating pastes on Jarvis's word, including encrypted burners. */
internal class PastebinActionHandler @Inject constructor(
    private val repository: PastebinRepository,
    private val echo: ActionEcho,
) : LifeActionHandler {

    override fun canHandle(action: LifeAction): Boolean = action is LifeAction.CreatePaste

    override suspend fun execute(action: LifeAction): LifeResult<Long?> {
        val create = action as LifeAction.CreatePaste
        val defaults = repository.shareDefaults()
        val result = repository.create(
            PasteRequest(
                title = create.title.ifBlank { "From Jarvis" }.take(80),
                content = create.content,
                expiry = defaults.expiry,
                visibility = defaults.visibility,
                burnAfterRead = create.burner,
                password = create.password,
            ),
            underAccount = repository.userKey().isNotBlank(),
        )
        return result.fold(
            onSuccess = { url ->
                echo.url(url)
                LifeResult.Success(null)
            },
            onFailure = { LifeResult.Failure(LifeError.Unknown(it.message ?: "Paste failed")) },
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PastebinJarvisModule {

    @Binds
    @IntoSet
    abstract fun bindProvider(impl: PastebinProvider): LifeDataProvider

    @Binds
    @IntoSet
    abstract fun bindHandler(impl: PastebinActionHandler): LifeActionHandler
}
