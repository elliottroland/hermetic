package hermetic.either

@JvmInline
value class Either<out E, out O> @PublishedApi internal constructor(@PublishedApi internal val value: Any?) {
    override fun toString() = fold({ "ok($it)" }, { "err($it)" })

    @Suppress("UNCHECKED_CAST")
    inline fun <K> fold(ifOk: (O) -> K, ifErr: (E) -> K): K =
        if (value is Err<*>) ifErr(value.value as E) else ifOk(value as O)

    @Suppress("UNCHECKED_CAST")
    inline fun onOk(ifOk: (O) -> Unit) = apply {
        if (value !is Err<*>) {
            ifOk(value as O)
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun onErr(ifErr: (E) -> Unit) = apply {
        if (value is Err<*>) {
            ifErr(value.value as E)
        }
    }
}

@PublishedApi internal data class Err<E>(@PublishedApi internal val value: E)

@Suppress("NOTHING_TO_INLINE")
inline fun <O> ok(value: O): Either<Nothing, O> = Either(value)

@Suppress("NOTHING_TO_INLINE")
inline fun <E> err(value: E): Either<E, Nothing> = Either(Err(value))

inline fun <E, O> Either<E, O>.getOr(ifErr: (E) -> O): O =
    fold({ it }, ifErr)

inline fun <E, O> Either<E, O>.errOr(ifOk: (O) -> E): E =
    fold(ifOk, { it })

inline fun <E, F, O> Either<E, O>.mapErr(ifErr: (E) -> F): Either<F, O> =
    fold({ ok(it) }, { err(ifErr(it))} )

class ErrPropagation(val err: Any?) : Throwable(null, null, false, false)

class EitherScope<E> {
    fun fail(e: E): Nothing {
        throw ErrPropagation(e)
    }
}

@Suppress("UNCHECKED_CAST")
inline fun <E, O> either(block: EitherScope<E>.() -> O): Either<E, O> =
    try {
        ok(EitherScope<E>().block())
    } catch (e: ErrPropagation) {
        err(e.err as E)
    }

inline fun <O> eitherCatching(block: () -> O): Either<Exception, O> =
    try {
        ok(block())
    } catch (e: Exception) {
        err(e)
    }