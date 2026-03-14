package hermetic.either

import kotlin.sequences.emptySequence
import kotlin.emptyArray

/**
 * When using [Either] we are able to model failures as any type. However, it can be useful
 * to know when specific failure types actually encode a [Throwable], so that for example
 * logging can be enriched.
 */
// interface Exceptional {
//     val message: String get() = "ERR $this"
//     val cause: Throwable? get() = null
//     val stackTrace: Array<StackTraceElement> get() = emptyArray()
// }

/**
 * Wraps a non-exception error as an exception. Can be useful for tracking failure points and connecting
 * errors to one another via cause or suppression relationships.
 */
open class ErrAsException(val error: Any, val generateStackTrace: Boolean = false) : IllegalStateException() {
    override val message = "ERR $error"
    override fun toString() = message

    init {
        if (!generateStackTrace) {
            stackTrace = emptyArray<StackTraceElement>()
        }
    }
}

/**
 * Wraps multiple errors together into a single exception with the given [message]. The errors are represented
 * as suppressions on the overarching exception so that they can still be seen.
 */
open class ErrsAsException(message: String, errors: List<Any>, generateStackTraces: Boolean = false) : ErrAsException(message, generateStackTrace = true) {
    constructor(errors: List<Any>, generateStackTraces: Boolean = false) : this("Unexpected error", errors, generateStackTraces)

    init {
        for (error in errors) {
            addSuppressed(throwable(error, generateStackTraces))
        }
    }

    fun clearStackTrace() = apply { this.stackTrace = emptyArray() }
}

fun throwable(error: Any, generateStackTrace: Boolean = false): Throwable =
    when {
        error is Throwable -> error
        else -> ErrAsException(error, generateStackTrace)
    }

// fun throwable(message: String, errors: List<Error>, generateStackTraces: Boolean = false): Throwable =
//     ErrsAsException(message, errors, generateStackTraces).also {
//         if (!generateStackTraces) { it.clearStackTrace() }
//     }