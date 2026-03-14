package hermetic

open class Err : IllegalStateException {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable) : super(cause)
    constructor(errors: List<Any>) : this("Encountered ${errors.size} errors (see suppressed)", errors)
    constructor(message: String, errors: List<Any>) : this(message) {
        for (error in errors) {
            addSuppressed(throwable(error))
        }
    }
    constructor(error: Any) : super(error.toString())

    companion object {
        fun throwable(error: Any): Throwable =
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
    throw Err(listOf(1, 2, "Hello", 4, IllegalStateException()))
}