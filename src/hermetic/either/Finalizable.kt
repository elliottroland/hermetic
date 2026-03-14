package hermetic.either

/**
 * An alternative of [AutoCloseable] which allows the implementation to specify a failure mode for the [finalize] method (the equivalent
 * to [AutoCloseable.close]) without throwing an exception. For interoperability with use cases that require an [AutoCloseable], the
 * [toAutoCloseable] function is provided.
 */
interface Finalizable<out E> {
    /**
     * Cleans the resources attached to this [Finalizable], possibly returning an error if something goes wrong.
     */
    fun finalize(): E?

    /**
     * Returns an [AutoCloseable] which will call [finalize] when [close]d. By default, if an error occurs during the [finalize] call it
     * will be ignored, but this can be changed by setting [throws] to true.
     */
    fun toAutoCloseable(throws: Boolean = false): AutoCloseable =
        AutoCloseableFinalizable(this, throws)
}

fun <F : Finalizable<E>, E, R> F.use(block: (F) -> R): FinalizedResult<E, R> {
    var result: FinalizedResult<E, R>? = null
    try {
        result = FinalizedResult(null, block(this))
        return result
    } finally {
        val error = finalize()
        val res = result?.result // If this is null, then we encountered an exception
        if (error != null && res != null) {
            return FinalizedResult(error, res)
        }
    }
}

class FinalizedResult<E, R>(val error: E?, val result: R) {
    fun onErr(block: (E) -> Unit) = apply { error?.also { block(it) } }
    fun toEither(): Either<E, R> = error?.let { err(it) } ?: ok(result)

    override fun toString() =
        when (error) {
            null -> "FinalizedResult($result)"
            else -> "FinalizedResult($result, error=$error)"
        }
}

class AutoCloseableFinalizable(val finalizable: Finalizable<*>, val throws: Boolean) : AutoCloseable {
    override fun close() {
        val error = finalizable.finalize()
        if (throws && error != null) {
            err(error).getOrThrow { "Unexpected error when finalizing $finalizable: $it" }
        }
    }
}