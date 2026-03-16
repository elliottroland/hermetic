package hermetic

/**
 * When modeling errors for use with [Either], it can be useful to still attach stacktraces to them in order to make it easier
 * to track where they occurred, or to throw an exception for a given error you've encountered even if it's not throwable.
 * The [Err] class provides a convenient way of handling these cases, by providing various constructors for which can coerce a
 * general (non-expectional) error type into an exception. When defining your own error types, subclass the [Err] type to get
 * access to the constructors for easily coercing different error types into an exception. When dealing with a possibly-throwable
 * error type from elsewhere, use the [throwable] function to coerce the error into a [Throwable], wrapping it in an [Err] as
 * necessary. Using [throwable] is generally recommended over calling the constructors directly, because it checks the type of
 * the error to make a better choice of constructor.
 */
open class Err : IllegalStateException {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable) : super(cause)
    /**
     * Construct an [Err] with a default message, coercing each of the [errors] to a [throwable] and marking them
     * as suppressed in favor of this new exception. This retains all the known error details while wrapping them
     * in a single exception.
     */
    constructor(errors: List<Any>) : this("Encountered ${errors.size} errors (see suppressed)", errors)
    /**
     * Construct an [Err] with the given [message], coercing each of the [errors] to a [throwable] and marking
     * them as suppressed in favor of this new exception.
     */
    constructor(message: String, errors: List<Any>) : this(message) {
        for (error in errors) {
            addSuppressed(throwable(error, generateStackTrace = false))
        }
    }
    /**
     * Constructs an [Err] with a default message and [error] coerced to a [throwable] marked as the cause.
     */
    constructor(error: Any) : this("Encountered error: $error", error)
    /**
     * Constructs an [Err] with the given [message] and [error] coerced to a [throwable] marked as the cause.
     */
    constructor(message: String, error: Any) : super(message, throwable(error, generateStackTrace = false))
}

/**
 * Clears the stacktrace of the [Err] in-place, returning the [Err] that this was called on.
 */
fun <E : Err> E.clearStackTrace(): E = apply { stackTrace = emptyArray() }

/**
 * Coerces the [error] into a [Throwable], either by casting it to one or by wrapping it in an [Err] based on its type.
 * If [generateStackTrace] is true (default: true), then if an [Err] is constructed it includes the stacktrace referencing
 * where this function was called from. If it is false, the returned [Throwable] will have no stacktrace. If [error] is
 * already a [Throwable], then [generateStackTrace] has no effect.
 */
fun throwable(error: Any, generateStackTrace: Boolean = true): Throwable =
    when (error) {
        is Throwable -> error
        else -> when (error) {
            is String -> Err(error)
            is List<*> -> Err(error.filterNotNull())
            else -> Err(error)
        }.apply { if (generateStackTrace) clearStackTrace() }
    }

fun main() {
    throw throwable(listOf(1, 2, "Hello", 4, IllegalStateException()))
}