package com.lifeos.core.common.result

/** Typed failure domain shared by every repository (§1.1). */
sealed interface LifeError {
    val message: String

    data class Database(override val message: String, val cause: Throwable? = null) : LifeError
    data class Network(override val message: String, val cause: Throwable? = null) : LifeError
    data class Crypto(override val message: String, val cause: Throwable? = null) : LifeError
    data class NotFound(override val message: String) : LifeError
    data class Validation(override val message: String) : LifeError
    data class Unknown(override val message: String, val cause: Throwable? = null) : LifeError
}
