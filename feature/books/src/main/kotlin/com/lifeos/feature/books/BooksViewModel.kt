package com.lifeos.feature.books

import androidx.lifecycle.viewModelScope
import com.lifeos.core.ai.AiRouter
import com.lifeos.core.ai.model.AiMessage
import com.lifeos.core.ai.model.AiRequest
import com.lifeos.core.ai.model.AiRole
import com.lifeos.core.common.coroutines.DispatcherProvider
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.viewmodel.LifeViewModel
import com.lifeos.core.database.books.BookDao
import com.lifeos.core.database.books.BookEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

data class BookSearchResult(val title: String, val author: String, val isbn: String?)

data class BooksUiState(
    val books: List<BookEntity> = emptyList(),
    val shelf: String = "ALL",
    val search: String = "",
    val searching: Boolean = false,
    val searchResults: List<BookSearchResult> = emptyList(),
    val recommendation: String? = null,
    val message: String? = null,
)

sealed interface BooksUiEvent {
    data class SearchChanged(val value: String) : BooksUiEvent
    data class AddBook(val result: BookSearchResult) : BooksUiEvent
    data class SelectShelf(val shelf: String) : BooksUiEvent
    data class CycleStatus(val id: Long, val current: String) : BooksUiEvent
    data class Rate(val id: Long, val halfStars: Int) : BooksUiEvent
    data class Delete(val id: Long) : BooksUiEvent
    data object Recommend : BooksUiEvent
    data object DismissMessage : BooksUiEvent
}

sealed interface BooksUiEffect

@OptIn(FlowPreview::class)
@HiltViewModel
class BooksViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val okHttpClient: OkHttpClient,
    private val aiRouter: AiRouter,
    private val dispatchers: DispatcherProvider,
) : LifeViewModel<BooksUiState, BooksUiEvent, BooksUiEffect>(BooksUiState()) {

    private val searchQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            bookDao.observeAll().collect { books -> updateState { it.copy(books = books) } }
        }
        viewModelScope.launch {
            searchQuery.debounce(600).collect { query ->
                if (query.length >= 3) searchOpenLibrary(query)
            }
        }
    }

    override fun onEvent(event: BooksUiEvent) {
        when (event) {
            is BooksUiEvent.SearchChanged -> {
                searchQuery.value = event.value
                updateState { it.copy(search = event.value) }
            }
            is BooksUiEvent.AddBook -> viewModelScope.launch {
                bookDao.insert(
                    BookEntity(
                        title = event.result.title,
                        author = event.result.author,
                        isbn = event.result.isbn,
                        status = "WANT",
                        ratingHalfStars = null,
                        notes = null,
                        addedAt = System.currentTimeMillis(),
                    ),
                )
                updateState { it.copy(search = "", searchResults = emptyList()) }
            }
            is BooksUiEvent.SelectShelf -> updateState { it.copy(shelf = event.shelf) }
            is BooksUiEvent.CycleStatus -> viewModelScope.launch {
                val next = when (event.current) {
                    "WANT" -> "READING"
                    "READING" -> "READ"
                    else -> "WANT"
                }
                bookDao.setStatus(event.id, next)
            }
            is BooksUiEvent.Rate -> viewModelScope.launch {
                bookDao.setRating(event.id, event.halfStars)
            }
            is BooksUiEvent.Delete -> viewModelScope.launch { bookDao.delete(event.id) }
            BooksUiEvent.Recommend -> recommend()
            BooksUiEvent.DismissMessage -> updateState { it.copy(message = null) }
        }
    }

    /** Open Library — keyless, [src 45]'s candidate source. */
    private fun searchOpenLibrary(query: String) {
        updateState { it.copy(searching = true) }
        viewModelScope.launch {
            try {
                val body = withContext(dispatchers.io) {
                    okHttpClient.newCall(
                        Request.Builder()
                            .url("https://openlibrary.org/search.json?limit=5&q=" + java.net.URLEncoder.encode(query, "UTF-8"))
                            .build(),
                    ).execute().use { it.body.string() }
                }
                val parsed = json.decodeFromString<OpenLibraryResponse>(body)
                updateState {
                    it.copy(
                        searching = false,
                        searchResults = parsed.docs.map { doc ->
                            BookSearchResult(
                                title = doc.title ?: "Untitled",
                                author = doc.author_name?.firstOrNull() ?: "Unknown",
                                isbn = doc.isbn?.firstOrNull(),
                            )
                        },
                    )
                }
            } catch (e: Exception) {
                updateState { it.copy(searching = false, message = e.message ?: "Search failed") }
            }
        }
    }

    /** On-device recs from your own shelves — no store algorithms ([src 45]). */
    private fun recommend() {
        viewModelScope.launch {
            val favourites = bookDao.favourites()
            if (favourites.isEmpty()) {
                updateState { it.copy(message = "Rate a few finished books first (3.5★+)") }
                return@launch
            }
            val request = AiRequest(
                system = "Suggest 3 books to read next with a one-line reason each, " +
                    "based only on the reader's favourites. Plain text list.",
                messages = listOf(
                    AiMessage(
                        AiRole.USER,
                        favourites.joinToString("\n") { "${it.title} by ${it.author} (${(it.ratingHalfStars ?: 0) / 2.0}★)" },
                    ),
                ),
            )
            when (val result = aiRouter.complete(request)) {
                is LifeResult.Success -> updateState { it.copy(recommendation = result.value.text) }
                is LifeResult.Failure -> updateState { it.copy(message = result.error.message) }
            }
        }
    }

    @Serializable
    private data class OpenLibraryResponse(val docs: List<Doc> = emptyList())

    @Suppress("PropertyName")
    @Serializable
    private data class Doc(
        val title: String? = null,
        val author_name: List<String>? = null,
        val isbn: List<String>? = null,
    )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
