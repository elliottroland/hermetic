package hermetic

/**
 * The recommended way of managing errors is through the err branch of the [Either][hermetic.either.Either].
 */
open class Err : IllegalStateException {
    protected constructor() : super()
    protected constructor(message: String) : super(message)
    protected constructor(message: String, cause: Throwable?) : super(message, cause)
    protected constructor(cause: Throwable) : super(cause)
    protected constructor(errors: List<Any>) : this("Encountered ${errors.size} errors (see suppressed)", errors)
    protected constructor(message: String, errors: List<Any>) : this(message) {
        for (error in errors) {
            addSuppressed(wrap(error))
        }
    }
    protected constructor(error: Any) : super(error.toString())

    companion object {
        fun wrap(error: Any): Throwable =
            when (error) {
                is Throwable -> error
                is String -> Err(error).clearStackTrace()
                is List<*> -> Err(error.filterNotNull()).clearStackTrace()
                else -> Err(error).clearStackTrace()
            }
    }
}

fun <E : Err> E.clearStackTrace(): E = apply { stackTrace = emptyArray() }

fun main() {
    throw Err.wrap(listOf(1, 2, "Hello", 4, IllegalStateException()))
}