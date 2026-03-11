package hermetic.effects

import hermetic.either.*
import kotlinx.coroutines.*

/**
 * A convenience wrapper around Kotlin's coroutine functionality, which allows for the management
 * of the lifecycle of coroutines. This is the suspending equivalent to [ManageAsync].
 */
interface SuspendAsync {
    suspend fun <R> async(waitForStart: Boolean, block: suspend () -> R): Awaitable<R>
    suspend fun <R> await(async: Awaitable<R>, timeoutMillis: Long): Either<Async.Failure, R>
    suspend fun cancel(async: Awaitable<*>)
}

// TODO
// A default implementation which wires up everything with a coroutine scope
// class SuspendManageAsyncDefault : SuspendManageAsync

// TODO
// An implementation which mirrors the ManageAsyncLazy
// class SuspendManageAsyncLazy : SuspendManageAsync

// TODO: We need a way of transferring coroutines from one context to another