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
 * many instances of this class. See [ErrsAsException] for an example.
 */
class ErrAsException(val error: Any, val hideStackTrace: Boolean = false) : IllegalStateException(), Exceptional {
    override val exception = this
    override val message = "Err $error"
    override fun toString() = message

    init {
        val exceptional = (error as? Exceptional).exception


        if (hideStackTrace) {
            stackTrace = emptyArray<StackTraceElement>()
        }
    }
}

/**
 * Wraps multiple errors together into a single exception with the given [message]. The errors are represented
 * as suppressions on the overarching exception so that they can still be seen.
 */
class ErrsAsException(
    message: String,
    errors: List<Any>,
    onlyExceptionStackTraces: Boolean
) : IllegalStateException(message) {
    init {
        for (error in errors) {
            if (error is Throwable) {
                addSuppressed(error)
            } else {
                addSuppressed(ErrAsException(error, hideStackTrace = onlyExceptionStackTraces))
            }
        }
    }
}