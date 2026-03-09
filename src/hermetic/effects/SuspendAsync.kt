package hermetic.effects

import hermetic.either.*
import kotlinx.coroutines.*

/**
 * A convenience wrapper around Kotlin's coroutine functionality, which allows for the management
 * of the lifecycle of coroutines. This is the suspending equivalent to [ManageAsync].
 */
interface SuspendManageAsync {
    suspend fun <R> async(waitForStart: Boolean, block: suspend () -> R): Async<R>
    suspend fun <R> await(async: Async<R>, timeoutMillis: Long): Either<Async.Failure, R>
    suspend fun cancel(async: Async<*>)
}

// TODO
// A default implementation which wires up everything with a coroutine scope
// class SuspendManageAsyncDefault : SuspendManageAsync

// TODO
// An implementation which mirrors the ManageAsyncLazy
// class SuspendManageAsyncLazy : SuspendManageAsync

// TODO: We need a way of transferring coroutines from one context to another