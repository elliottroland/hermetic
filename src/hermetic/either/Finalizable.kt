package hermetic.either

import hermetic.either.FinalizedResult
import hermetic.either.FinalizationException

interface Finalizable<out E> {
    fun finalize(): E?
    fun toCloseable(throwErr: Boolean = false): AutoCloseable = AutoCloseableFinalizable(this, throwErr)
}

open class FinalizationException(message: String, errors: List<Any> = emptyList()) : IllegalStateException(message) {
    init {
        for (error in errors) {
            if (error is Throwable) {
                addSuppressed(error)
            } else {
                addSuppressed(ErrAsException(error))
            }
        }
    }
}

fun <F : Finalizable<E>, E, R> F.use(block: (F) -> R): FinalizedResult<E, R> {
    var result: FinalizedResult<E, R>? = null
    try {
        result = FinalizedResult(block(this))
        return result
    } finally {
        result?.error = finalize()
    }
}

class FinalizedResult<E, R>(val result: R, error: E? = null) {
    var error: E? = error
        internal set
    
    fun onErr(block: (E) -> Unit) = apply { error?.also { block(it) } }

    operator fun component1(): E? = error
    operator fun component2(): R = result

    fun toEither(): Either<E, R> = error?.let { err(it) } ?: ok(result)

    override fun toString() =
        when (error) {
            null -> "FinalizedResult($result)"
            else -> "FinalizedResult($result, error=$error)"
        }
    override fun hashCode() = result.hashCode()
    override fun equals(other: Any?) = other is FinalizedResult<*, *> && result == other.result && error == other.error
}

class AutoCloseableFinalizable(val finalizable: Finalizable<*>, val throwErr: Boolean) : AutoCloseable {
    override fun close() {
        val error = finalizable.finalize()
        if (throwErr && error != null) {
            err(error).getOrThrow { "Unexpected error when finalizing $finalizable: $it" }
        }
    }
}