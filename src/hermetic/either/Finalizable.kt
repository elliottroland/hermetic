package hermetic.either

import java.io.Closeable
import hermetic.either.FinalizedResult

interface Finalizable<out E> {
    fun finalize(): E?
    fun toCloseable(throwErr: Boolean = false): Closeable = CloseableFinalizable(this, throwErr)
}

class FinalizedResult<R, E>(val result: R, error: E? = null) {
    var error: E? = error
        internal set
    
    fun onErr(block: (E) -> Unit) = apply { error?.also { block(it) } }

    operator fun component1(): E? = error
    operator fun component2(): R = result

    override fun toString() =
        when (error) {
            null -> "FinalizedResult($result)"
            else -> "FinalizedResult($result, error=$error)"
        }
    override fun hashCode() = result.hashCode()
    override fun equals(other: Any?) = other is FinalizedResult<*, *> && result == other.result && error == other.error
}

fun <F : Finalizable<E>, E, R> F.use(block: (F) -> R): FinalizedResult<R, E> {
    var result: FinalizedResult<R, E>? = null
    try {
        result = FinalizedResult(block(this))
        return result
    } finally {
        result?.error = finalize()
    }
}

class CloseableFinalizable(val finalizable: Finalizable<*>, val throwErr: Boolean) : Closeable {
    override fun close() {
        val error = finalizable.finalize()
        if (throwErr && error != null) {
            err(error).getOrThrow { "Unexpected error when finalizing $finalizable: $it" }
        }
    }
}