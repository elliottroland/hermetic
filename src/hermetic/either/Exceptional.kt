package hermetic.either

import kotlin.sequences.emptySequence
import kotlin.emptyArray

/**
 * When using [Either] we are able to model failures as any type. However, it can be useful
 * to know when specific failure types actually encode a [Throwable], so that for example
 * logging can be enriched.
 */
interface Exceptional {
    val exception: Throwable?
}

/**
 * Wraps a non-exception error as an exception. Can be useful for tracking failure points and connecting
 * errors to one another via cause or suppression relationships.
 * 
 * You can optionally hide the stack trace, which is useful for avoiding cluttering up stack traces with
 * many instances of this class. See [FinalizationError] for an example.
 */
data class ErrAsException(val error: Any, val hideStackTrace: Boolean = false) : IllegalStateException(), Exceptional {
    override val exception = this
    override val message = "ErrAsException $error"
    override fun toString() = message

    init {
        if (hideStackTrace) {
            stackTrace = emptyArray<StackTraceElement>()
        }
    }
}