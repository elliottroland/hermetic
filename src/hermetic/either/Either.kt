package hermetic.either

import hermetic.throwable

/**
 * Represents the outcome of a function, either a failure of type [E] or a success of type [O]. An [Either] cannot be constructed directly, but must be
 * done via one of the branch methods ([err] and [ok]), or through the [either] scope function. Various mechanisms exist for working with the value of
 * an [Either]:
 *
 * * [fold] is the most general, allowing the value to be transformed according to which branch is present.
 * * "on" functions ([onOk], [onErr]) allow for actions to be taken depending on which branch is present.
 * * "get" functions ([getOr], [getOrNull], [errOr], [errOrNull]) conditionally extract the value of the relevant branch, or some substitute value.
 * * "map" functions ([map], [mapErr], [flatMap], [flatMapErr]) allow the transformation of the branch the [Either] is currently on.
 * * "recover" functions ([recover], [recoverIf], [recoverToNullIf], [tryRecover]) provides ways to move from the [err] branch to the [ok] branch.
 * * In the [either] scope, "fail" ([getOrFail][EitherScope.getOrFail], [fail][EitherScope.fail]) provide ways to short-circuit the scope with an [err].
 *
 * Most functions are inlined in order to reduce overhead of using [Either]s in the majority of cases, and the way [Either] stores it values is designed
 * to minimize the overhead and objects created when on the [ok] branch.
 *
 * ### Examples
 * ```
 * // It is recommended that you model failures explicitly and exhaustively
 * sealed interface Failure {
 *   object Throttled : Failure
 *   object ItemDoesNotExist : Failure
 *   data class Unknown(val message: String, val cause: Exception?) : Failure
 * }
 *
 * // Some possibly failing code
 * fun getItem(key: String): Either<Failure, Item> {}
 *
 * // Will be null if we encountered an error
 * fun nullIfError(): Item? =
 *   getItem(key).getOrNull()
 *
 * // Will apply a transformation on the error before returning
 * fun mapError(): Either<OtherFailure, Item> =
 *   getItem(key).mapErr { err -> mapToOtherFailure(err) }
 *
 * // Will map the ItemDoesNotExist case to a null item, but keep the other errors
 * fun nullIfNotExists(): Either<Failure, Item?> =
 *   getItem(key).recoverToNullIf { it is Failure.ItemDoesNotExist }
 *
 * // Will log the error if we encounter it, and continue on if not
 * fun logError(): Either<Failure, String> =
 *   getItem(key).onErr { err -> log.error("Encountered error: $err") }.map { item -> item.id }
 *
 * // Combine two items if they both exist, otherwise short-circuit
 * fun combine(): Either<Failure, String> = either {
 *   // Errors will be propagated up to the [either] boundary, which in this context we call a "failure"
 *   val item1: Item = getItem(key).getOrFail()
 *   val item2: Item = getItem(key).getOrFail()
 *
 *   // Evaluate some combination of the two items
 *   "${item1.id} + ${item2.id}"
 * }
 * ```
 * 
 * ### Eithers and Pairs
 * Each [Either] can be destructred into its err and ok values (at most one of which will be non-null), and can be directly converted to a [Pair] using
 * [toPair] for contexts where that it more useful:
 * ```
 * // Some possibly failing code
 * fun getItem(key: String): Either<Failure, Item> {}
 * 
 * val (err, ok) = getItem(key)
 * if (err != null) {
 *   // Deal with error
 * }
 * ```
 *
 * ### Comments on naming
 * It is common to see types like this called "Result", but Kotlin already defines a [Result] type which does not allow us to scope down the failure
 * types of the result (it accepts any [Throwable]). In order to avoid confusion, we have gone with a common alternative found in languages like Haskell
 * and Unison. However, in those languages the two branches are often called "left" and "right", and the success branch is on the right by convention.
 * Since the primary expected use case for [Either] is model success or failure, we this implementation uses the more specific branch names to convey
 * this. This choice allows us to streamline the function names on the success path, since this is the "expected" value: we call it [getOrNull] rather
 * than `rightOrNull`, and [flatMap] rather than `flatMapRight`. Finally, in order to make building the branches as unobtrusive as possible, we have
 * followed the naming convention from Rust, and called them [ok] and [err].
 */
@JvmInline
value class Either<out E, out O> @PublishedApi internal constructor(@PublishedApi internal val value: Any?) {
    val isErr: Boolean get() = value is ErrMarker<*>
    val isOk: Boolean get() = value !is ErrMarker<*>

    override fun toString() = fold({ "ok($it)" }, { "err($it)" })

    /**
     * Applies [ifOk] if this is [ok], or [ifErr] if this is [err], and returns the result.
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <K> fold(ifOk: (O) -> K, ifErr: (E) -> K): K =
        if (value is ErrMarker<*>) ifErr(value.value as E) else ifOk(value as O)

    /**
     * Run the [block] if this [Either] is [ok], otherwise do nothing, then return this.
     */
    @Suppress("UNCHECKED_CAST")
    fun onOk(block: (O) -> Unit): Either<E, O> = apply {
        if (isOk) {
            block(this.value as O)
        }
    }

    /**
     * Run the [block] if this [Either] is [err], otherwise do nothing, then return this.
     */
    @Suppress("UNCHECKED_CAST")
    fun onErr(block: (E) -> Unit): Either<E, O> = apply {
        if (isErr) {
            block((value as ErrMarker<E>).value)
        }
    }

    /**
     * Equivalent to [errOrNull], allowing for the destructing of an [Either] into its err and ok values.
     * ```
     * val (err, ok) = someEither
     * ```
     */
    operator fun component1(): E? = errOrNull()

    /**
     * Equivalent to [getOrNull], allowing for the destructing of an [Either] into its err and ok values.
     * ```
     * val (err, ok) = someEither
     * ```
     */
    operator fun component2(): O? = getOrNull()

    /**
     * Convert this [Either] into a [Pair].
     */
    fun toPair(): Pair<E?, O?> = Pair(component1(), component2())
}

/**
 * An instance of [Either] which represents the expected outcome of a computation.
 */
@PublishedApi internal data class ErrMarker<out E>(val value: E)

// ------ "Constructors" ------

/**
 * Builds an [Either] on the ok branch, representing a successful outcome. This is the "expected" value of an [Either], and is therefore the
 * value on which functions like [getOrNull] or [map] operate.
 */
fun <T> ok(t: T): Either<Nothing, T> = Either(t)

/**
 * Builds an [Either] on the err branch, representing a failed outcome. This is the "unexpected" value of an [Either], and is therefore the
 * value on which functions like [errOrNull] or [mapErr] operate.
 *
 * The recovery functions ([recover], [recoverIf], [recoverToNullIf]) are designed to allow this branch to map back to the [ok] branch.
 */
fun <T> err(t: T): Either<T, Nothing> = Either(ErrMarker(t))

// ------ Getting ------

/**
 * Returns the value in this [Either] if it is on the [ok] branch, otherwise returns the value of [ifErr]. This function is inlined so
 * that you can use non-local returns rather than actually computing a value of type [O]:
 * ```
 * fun myFunction(): String {
 *   val result = possiblyFailingCall().getOr { err -> return "ERROR: ${err.message}" }
 *   // does not run if previous call failed
 * }
 * ```
 * [getOr] and [errOr] can be thought of as generalizations of Kotlin's Elvis operator.
 */
inline fun <E, O> Either<E, O>.getOr(ifErr: (E) -> O): O =
    fold({ it }, ifErr)

/**
 * Returns the value in this [Either] if it is on the [err] branch, otherwise returns the value of [ifOk]. This function is inlined so
 * that you can use non-local returns rather than actually computing a value of type [E]:
 * ```
 * fun myFunction(): String {
 *   val error = possiblyFailingCall().errOr { result -> return result }
 *   // does not run if previous call succeeded
 * }
 * ```
 * [getOr] and [errOr] can be thought of as generalizations of Kotlin's Elvis operator.
 */
inline fun <E, O> Either<E, O>.errOr(ifOk: (O) -> E): E =
    fold(ifOk, { it })

/**
 * Returns the value in this [Either] if it is on the [ok] branch, otherwise returns null. This is equivalent to `getOr { null }`.
 */
// Note: This is made inline so that we can avoid function call overheads.
fun <E, O> Either<E, O>.getOrNull(): O? =
    getOr { null }

/**
 * Returns the value in this [Either] if it is on the [err] branch, otherwise returns null. This is equivalent to `errOr { null }`.
 */
// Note: This is made inline so that we can avoid function call overheads.
fun <E, O> Either<E, O>.errOrNull(): E? =
    errOr { null }

// ------ Mapping ------

/**
 * Applies the [mapper] if this is [ok], wrapping the result in an [Either], otherwise returns this as is. This is equivalent to `flatMap { ok(mapper(it) }`.
 */
fun <E, O, P> Either<E, O>.map(mapper: (O) -> P): Either<E, P> =
    flatMap { ok(mapper(it)) }

/**
 * Applies the [mapper] if this is [err], wrapping the result in an [Either], otherwise returns this as is. This is equivalent to `flatMapErr { err(mapper(it) }`.
 */
fun <E, F, O> Either<E, O>.mapErr(mapper: (E) -> F): Either<F, O> =
    fold({ ok(it) }, { err(mapper(it)) })

// ------ Flat mapping ------

/**
 * Applies the [mapper] if this is [ok], returning the result, otherwise returns this as is. The [mapper] is allowed to generalize the error type in this
 * [Either], but if a completely disjoint error type is needed then use [Either.fold] instead.
 */
fun <E : F, F, O, P> Either<E, O>.flatMap(mapper: (O) -> Either<F, P>): Either<F, P> =
    fold({ mapper(it) }, { err(it) })

// ------ Recovery ------

/**
 * Attempts to [recover] an [err] with a function that itself might fail in a new way. This can be seen as a [flatMapErr] which allows you to cross
 * from the [err] branch to the [ok] branch.
 * ```
 * fun possiblyFailingFunction(): Either<Failure, String> {
 *   // Some possibly failing code
 * }
 *
 * fun possiblyFailingRecovery(failure: Failure): Either<OtherFailure, String> {
 *   // Some more possibly failing code
 * }
 *
 * fun myFunction(): String {
 *   val result: Either<OtherFailure, String> = possiblyFailingFunction()
 *     .recover { err -> possiblyFailingRecovery(err) }
 * }
 * ```
 */
fun <E, F, O : P, P> Either<E, O>.recover(transform: (E) -> Either<F, P>): Either<F, P> =
    fold({ ok(it) }, { transform(it) })

/**
 * Applies the [transform] if this is an [err], wrapping the result as the [ok] branch of an
 * [Either].
 * ```
 * fun possiblyFailingFunction(): Either<Failure, String> {
 *   // Some possibly failing code
 * }
 *
 * fun myFunction(): String {
 *   val result: String = possiblyFailingFunction()
 *     .fullRecover { err -> "ERROR: ${err.message}" }
 *     .getOrNull()!! // will never fail
 * }
 * ```
 *
 * It is not generally expected that you'll be able to recover every failure case, so that
 * [recoverIf] or [recover] might be a better options.
 */
fun <E, O : P, P> Either<E, O>.fullRecover(transform: (E) -> P): Either<Nothing, P> =
        fold({ ok(it) }, { ok(transform(it)) })

/**
 * Attempts to [recover] an [err] branch using the [transform] if the [condition] is met, otherwise returns this as is. For example, suppose you want
 * to model non-existence with a default value, but the function you're calling models it as a failure:
 * ```
 * sealed interface Failure {
 *   object Throttled : Failure
 *   object ItemDoesNotExist : Failure
 *   data class Unknown(val message: String, val cause: Exception?) : Failure
 * }
 *
 * fun getItem(key: String): Either<Failure, Item> {
 *   // Returns err(ItemDoesNotExist) if item not found
 * }
 *
 * fun myFunction() {
 *   // Will be the value if the item exists, defaultItemValue() if it doesn't, or null for any other failure.
 *   val item: Item? = getItem(key)
 *     .recoverIf({ it is Failure.ItemDoesNotExist }) { defaultItemValue() }
 *     .getOrNull()
 * }
 * ```
 */
fun <E, O : P, P> Either<E, O>.recoverIf(condition: (E) -> Boolean, transform: (E) -> P): Either<E, P> =
    fold({ this }, { if (condition(it)) ok(transform(it)) else this })

/**
 * Recovers an [err] branch to `null` if the [condition] is met, otherwise returns this as is. This is equivalent to `recoverIf(condition) { null }`.
 * For example, suppose you want to model non-existence with a null, but the function you're calling models it as a failure:
 * ```
 * sealed interface Failure {
 *   object Throttled : Failure
 *   object ItemDoesNotExist : Failure
 *   data class Unknown(val message: String, val cause: Exception?) : Failure
 * }
 *
 * fun getItem(key: String): Either<Failure, Item> {
 *   // Returns err(ItemDoesNotExist) if item not found
 * }
 *
 * fun myFunction() {
 *   // Will be null if the item did not exist, but will short-circuit the function if it's any other failure.
 *   val item: Item? = getItem(key)
 *     .recoverToNullIf({ it is Failure.ItemDoesNotExist })
 *     .getOr { return }
 * }
 * ```
 */
fun <E, O> Either<E, O>.recoverToNullIf(condition: (E) -> Boolean): Either<E, O?> =
    recoverIf(condition) { null }

// ------ Dealing with exceptions ------

/**
 * Throws an [IllegalStateException] with the given [message] if this is an [err], otherwise returns the [ok].
 * If the [err] is an instance of [Exceptional], then its exception is used to populate the cause of the thrown exception.
 */
fun <E : Any, O> Either<E, O>.getOrThrow(message: (E) -> String = { it.toString() }): O =
    // TODO: need to use message
    getOr { err -> throw throwable(err) }

/**
 * A version of [either] which catches and wraps all non-fatal exceptions. Ideally used when interoperating with existing code which is
 * known to throw exceptions rather than work with [Either]s.
 */
fun <O> eitherCatching(block: () -> O): Either<Exception, O> =
    try {
        ok(block())
    } catch (e: Exception) {
        err(e)
    }
