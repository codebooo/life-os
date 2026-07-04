package com.lifeos.core.common.result

/** Result type returned by suspend repository writes (§1.6). */
sealed interface LifeResult<out T> {
    data class Success<T>(val value: T) : LifeResult<T>
    data class Failure(val error: LifeError) : LifeResult<Nothing>
}

inline fun <T> LifeResult<T>.onSuccess(block: (T) -> Unit): LifeResult<T> {
    if (this is LifeResult.Success) block(value)
    return this
}

inline fun <T> LifeResult<T>.onFailure(block: (LifeError) -> Unit): LifeResult<T> {
    if (this is LifeResult.Failure) block(error)
    return this
}

fun <T> LifeResult<T>.getOrNull(): T? = (this as? LifeResult.Success)?.value

/** Runs [block], mapping any thrown exception through [mapError]. */
inline fun <T> runCatchingLife(
    mapError: (Throwable) -> LifeError = { LifeError.Unknown(it.message ?: "Unknown error", it) },
    block: () -> T,
): LifeResult<T> = try {
    LifeResult.Success(block())
} catch (t: Throwable) {
    if (t is kotlinx.coroutines.CancellationException) throw t
    LifeResult.Failure(mapError(t))
}
