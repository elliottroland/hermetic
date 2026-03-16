package hermetic.effects

import hermetic.either.Either
import hermetic.either.eitherCatching
import hermetic.either.err
import hermetic.either.mapErr
import hermetic.either.ok
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

val log = Log("effects.Async")

fun main() {
    context(AsyncDefault(), LoggingConsole(), WaitDefault()) {
        async { sayHello("Roland") }
        log.info("Computing async")
        val sum = async(waitForStart = true) { log.info("Async started"); 1 + 2 }
        waitMillis(1_000)
        log.info("Awaiting...")
        val result = sum.await()
        log.info("Result: $result")
    }

    // val result = context(LoggingConsole(), AsyncLazy()) {
    //     val sum = async { 1 + 2 }
    //     log.info(sum.await())

    //     context(WaitDefault()) {
    //         val loop = runForever()
    //         waitMillis(1_000)
    //         loop.cancel("Testing that cancellation works as expected")
    //         loop.await().getOr { log.error(it) }
    //     }
    // }
}

context(_: Async, _: Logging, _: Wait)
fun runForever(): Awaitable<Nothing> =
    async {
        while (true) {
            log.info("Waiting some amount of time")
            waitMillis(1_000)
        }
        nothing()
    }

context(_: Wait, _: Logging)
fun sayHello(name: String) {
    waitMillis(1_000)
    log.info("Hello, $name!")
}

fun nothing(): Nothing = throw IllegalStateException()

/**
 * An effect which defines ways of creating and interacting with asynchronous computations. Whether these implementations
 * run in a separate thread, evaluate lazily, etc. is all determined by the various implementations. Implementations should
 * always return from [async] without blocking on the completion of the provided block.
 */
interface Async {
    fun <R> async(waitForStart: Boolean, block: () -> R): Awaitable<R>
    fun <R> await(async: Awaitable<R>, timeoutMillis: Long): Either<Async.Failure, R>
    fun cancel(async: Awaitable<*>, reason: String?)

    sealed interface Failure {
        data class Timeout(override val cause: Throwable) : Failure, Exception()
        data class Cancelled(override val cause: Throwable) : Failure, Exception()
        data class Unknown(override val cause: Throwable) : Failure, Exception()
    }

    companion object {
        val IO: Async = AsyncDefault(namePrefix = "async-io", daemon = true)
    }
}

/**
 * A "bare particular" used by the various implementations of [Async]. This interface serves only
 * as a tag for the item used by these implementations, and a namespace for the [Async.Failure] type.
 */
interface Awaitable<out R>

@Suppress("NOTHING_TO_INLINE")
context(t: Async)
inline fun <R> async(waitForStart: Boolean = true, noinline block: () -> R): Awaitable<R> =
    t.async(waitForStart, block)

@Suppress("NOTHING_TO_INLINE")
context(t: Async)
inline fun <R> Awaitable<R>.await(timeoutMillis: Long = Long.MAX_VALUE): Either<Async.Failure, R> =
    t.await(this, timeoutMillis)

@Suppress("NOTHING_TO_INLINE")
context(t: Async)
inline fun <R> Awaitable<R>.cancel(reason: String? = null) =
    t.cancel(this, reason)

/**
 * An implementation of [Async] which resolves [async] results in separate threads backed by
 * the [executor]. Convenience constructors are provided for creating an infinitely-scaling cached
 * thread pool which provides equivalent behavior to spinning up threads independently.
 */
class AsyncDefault(
    val executor: ExecutorService
) : Async {
    constructor(
        threadFactory: ThreadFactory
    ) : this(Executors.newCachedThreadPool(threadFactory))

    constructor(
        namePrefix: String = "thread",
        daemon: Boolean = true
    ) : this(DefaultThreadFactory(namePrefix, daemon))

    override fun <R> async(waitForStart: Boolean, block: () -> R): Awaitable<R> {
        val latch = if (waitForStart) CountDownLatch(1) else null
        val async = CompletableAwaitable.from(executor) { latch?.countDown(); block() }
        latch?.await()
        return async
    }

    override fun <R> await(async: Awaitable<R>, timeoutMillis: Long): Either<Async.Failure, R> {
        require(async is CompletableAwaitable<R>)
        return try {
            ok(async.future.get(timeoutMillis, TimeUnit.MILLISECONDS))
        } catch (e: TimeoutException) {
            err(Async.Failure.Timeout(e))
        } catch (e: CancellationException) {
            err(Async.Failure.Cancelled((e.cause as? CancellationException) ?: e))
        } catch (e: ExecutionException) {
            // This is like the catch-all, except we try to unwrap a level of the execution failure, since these
            // just add noise.
            err(Async.Failure.Unknown(e.cause ?: e))
        } catch (e: Exception) {
            err(Async.Failure.Unknown(e))
        }
    }

    override fun cancel(async: Awaitable<*>, reason: String?) {
        require(async is CompletableAwaitable<*>)
        async.future.completeExceptionally(CancellationException(reason ?: "Async was manually cancelled"))
    }
    
    private class DefaultThreadFactory(val namePrefix: String, val daemon: Boolean) : ThreadFactory {
        override fun newThread(r: Runnable): Thread = Thread(r).apply {
            name = "$namePrefix-${UUID.randomUUID().toString().substringBefore('-')}"
            isDaemon = daemon
        }
    }

    private class CompletableAwaitable<R>(val future: CompletableFuture<R>) : Awaitable<R> {
        companion object {
            fun <R> from(executor: Executor, block: () -> R) =
                CompletableAwaitable(CompletableFuture.supplyAsync(block, executor))
        }
    }
}

/**
 * An implementation of [Async] which evaluates the [Async] lazily when it is awaited. The same asyncs
 * can be re-awaited multiple times, but the same result will be returned.
 */
class AsyncLazy : Async {
    override fun <R> async(waitForStart: Boolean, block: () -> R): Awaitable<R> {
        check(!waitForStart) { "Lazy async will never start" }
        return LazyAwaitable(block)
    }
    
    override fun <R> await(async: Awaitable<R>, timeoutMillis: Long): Either<Async.Failure, R> {
        require(async is LazyAwaitable<R>)
        return when (val cancellation = async.cancellation) {
            null -> eitherCatching { async.value }.mapErr { Async.Failure.Unknown(it) }
            else -> err(Async.Failure.Cancelled(cancellation))
        }
    }

    override fun cancel(async: Awaitable<*>, reason: String?) {
        require(async is LazyAwaitable<*>)
        async.cancellation = async.cancellation ?: CancellationException(reason ?: "Manually cancelled")
    }

    class LazyAwaitable<R>(block: () -> R) : Awaitable<R> {
        val value: R by lazy { block() }
        @Volatile var cancellation: CancellationException? = null
    }
}
