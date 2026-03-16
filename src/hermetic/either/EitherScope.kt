package hermetic.either

import hermetic.DeferBlockScope
import hermetic.DeferScope

/**
 * Executes the [block] with an [EitherScope], wrapping the successful result with [ok] and wrapping
 * any failures with [err]. Within the scope, failure is managed via the extra functions provided,
 * such as [getOrFail][EitherScope.getOrFail] and [fail][EitherScope.fail], which short-circuit the
 * block for the [err] branch.
 *
 * ### Defer
 * The scope defined for this block is also a [DeferScope], allowing the use of [defer][DeferScope.defer]
 * for running code after the block is finished in reverse order.
 *
 * The response which will be returned by the [either] is passed to each deferral, allowing them to
 * change their behavior based on it (although this is not recommended in general, as it complicates
 * the logic). Each deferral runs within the same [scope][EitherScope] as the rest of the block,
 * meaning that it can decide to ultimately fail using the [getOrFail][EitherScope.getOrFail] and
 * [fail][EitherScope.fail] functions.
 *
 * A deferral's failure will override a successful response from the either. If the either block
 * itself failed, then this error is kept. If two deferrals fail, then the earliest failure in time
 * (reverse of definition) is the one which is chosen.
 * ```
 * either {
 *   val dir = fs.createDir("my-dir").getOrFail()
 *   defer { fs.delete(dir).getOrFail() }
 *
 *   val file = fs.createFile(dir, "my-file")
 *   defer { fs.delete(file).getOrFail() }
 *
 *   // Do work with file
 * } // File will be deleted, then the directory
 * ```
 *
 * ### Multiple nested scopes
 * While it is not recommended (because it is difficult to read), you can nest [either] scopes and
 * break to outer ones using [with]:
 * ```
 * either outer@{
 *   // runs
 *   either inner@{
 *     // runs
 *     with(this@outer) { fail(X) }
 *     // does not run
 *   }
 *   // does not run
 * } // resolves to err(X)
 * ```
 */
inline fun <E, O> either(block: EitherDeferScope<E, O>.() -> O): Either<E, O> {
    val scope = EitherDeferScope<E, O>()
    var result: Either<E, O>? = null
    try {
        result = ok(scope.block())
    } catch (e: ErrPropagation) {
        when {
            e.scope === scope -> result = Either(e.value)
            else -> throw e
        }
    } finally {
        if (scope.hasDeferrals()) {
            for (deferral in scope.deferrals) {
                try {
                    scope.deferral()
                } catch (e: ErrPropagation) {
                    when {
                        e.scope === scope -> {
                            if (result?.isErr != true) {
                                result = Either(e.value)
                            }
                        }
                        else -> throw e
                    }
                }
            }
        }
    }
    return result!!
}


/** See [EitherScope] for details on how this is used. */
@PublishedApi internal class ErrPropagation(val value: ErrMarker<*>, val scope: EitherScope<*, *>) : Throwable(null, null, false, false)

interface EitherScope<E, O> : DeferBlockScope {
    /**
     * Returns the value in this [Either] if it is on the [ok] branch, otherwise fails the current
     * [either] scope with the value in the [err] branch:
     * ```
     * fun myFunction(): Either<Failure, String> = either {
     *   val result = possiblyFailingCall().getOrFail()
     *   // does not run if previous call failed
     * }
     * ```
     */
    fun <O> Either<E, O>.getOrFail(): O

    /**
     * Returns the value in this [Either] if it is on the [ok] branch, otherwise fails the current
     * [either] scope with the result of the [mapper]. This allows you to transform failures into the
     * expected type before short-circuiting the current [either] scope.
     * ```
     * fun possiblyFailingCall(): Either<Failure, String> {
     *   // Some possibly failing code
     * }
     *
     * fun mapFailure(failure: Failure): OtherFailure {
     *   // Maps between received and expected failures
     * }
     *
     * fun myFunction(): Either<OtherFailure, String> = either {
     *   val result = possiblyFailingCall().getOrFail { err -> mapFailure(err) }
     *   // does not run if previous call failed
     * }
     * ```
     */
    fun <F, O> Either<F, O>.getOrFail(mapper: (F) -> E): O

    /**
     * Short-circuits the current [either] scope, resolving it's overall value to the [err] branch with
     * value [e]:
     * ```
     * either {
     *   // runs
     *   fail(X)
     *   // does not run
     * } // resolves to err(X)
     * ```
     */
    fun fail(e: E): Nothing
}

/**
 * The scope in which [either]s are executed, which provides more functionality for controlling the
 * flow of the function in ways that are not supported in general. Within an [either] scope, the
 * return value is wrapped in an [ok], while any failures short-circuit the block and determine the
 * value in an [err].
 *
 * @see either
 */
class EitherDeferScope<E, O> : EitherScope<E, O>, DeferScope<EitherScope<E, O>>() {
    // ------ Failures ------

    override fun <O> Either<E, O>.getOrFail(): O =
        getOr { fail(it) }

    override fun <F, O> Either<F, O>.getOrFail(mapper: (F) -> E): O =
        getOr { fail(mapper(it)) }

    override fun fail(e: E): Nothing =
        throw ErrPropagation(ErrMarker(e), this@EitherDeferScope)
}
