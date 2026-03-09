package hermetic.effects

import hermetic.either.*
import java.util.concurrent.*
import java.util.UUID

val log = Log("effects.Async")

fun main() {
    context(ManageAsyncDefault(), WriteLogConsole(), WaitDefault()) {
        async { sayHello("Roland") }
        log.info("Computing async")
        val sum = async(waitForStart = true) { log.info("Async started"); 1 + 2 }
        waitMillis(1_000)
        log.info("Awaiting...")
        val result = sum.await()
        log.info("Result: $result")
    }

    // val result = context(WriteLogConsole(), ManageAsyncLazy()) {
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

context(_: ManageAsync, _: WriteLog, _: Wait)
fun runForever(): Async<Nothing> =
    async {
        while (true) {
            log.info("Waiting some amount of time")
            waitMillis(1_000)
        }
        nothing()
    }

context(_: Wait, _: WriteLog)
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
interface ManageAsync {
    fun <R> async(waitForStart: Boolean, block: () -> R): Async<R>
    fun <R> await(async: Async<R>, timeoutMillis: Long): Either<Async.Failure, R>
    fun cancel(async: Async<*>, reason: String?)
}

/**
 * A "bare particular" used by the various implementations of [ManageAsync]. This interface serves only
 * as a tag for the item used by these implementations, and a namespace for the [Async.Failure] type.
 */
interface Async<out R> {
    sealed interface Failure {
        data class Timeout(override val exception: Throwable) : Failure, Exceptional
        data class Cancelled(override val exception: Throwable) : Failure, Exceptional
        data class Unknown(override val exception: Throwable) : Failure, Exceptional
    }
}

@Suppress("NOTHING_TO_INLINE")
context(t: ManageAsync)
inline fun <R> async(waitForStart: Boolean = true, noinline block: () -> R): Async<R> =
    t.async(waitForStart, block)

@Suppress("NOTHING_TO_INLINE")
context(t: ManageAsync)
inline fun <R> Async<R>.await(timeoutMillis: Long = Long.MAX_VALUE): Either<Async.Failure, R> =
    t.await(this, timeoutMillis)

@Suppress("NOTHING_TO_INLINE")
context(t: ManageAsync)
inline fun <R> Async<R>.cancel(reason: String? = null) =
    t.cancel(this, reason)

/**
 * An implementation of [ManageAsync] which resolves [async] results in separate threads backed by
 * the [executor]. Convenience constructors are provided for creating an infinitely-scaling cached
 * thread pool which provides equivalent behavior to spinning up threads independently.
 */
class ManageAsyncDefault(
    val executor: ExecutorService
) : ManageAsync {
    constructor(
        threadFactory: ThreadFactory
    ) : this(Executors.newCachedThreadPool(threadFactory))

    constructor(
        namePrefix: String = "thread",
        daemon: Boolean = true
    ) : this(DefaultThreadFactory(namePrefix, daemon))

    override fun <R> async(waitForStart: Boolean, block: () -> R): Async<R> {
        val latch = if (waitForStart) CountDownLatch(1) else null
        val async = CompletableAsync.from(executor) { latch?.countDown(); block() }
        latch?.await()
        return async
    }

    override fun <R> await(async: Async<R>, timeoutMillis: Long): Either<Async.Failure, R> {
        require(async is CompletableAsync<R>)
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

    override fun cancel(async: Async<*>, reason: String?) {
        require(async is CompletableAsync<*>)
        async.future.completeExceptionally(CancellationException(reason ?: "Async was manually cancelled"))
    }
    
    private class DefaultThreadFactory(val namePrefix: String, val daemon: Boolean) : ThreadFactory {
        override fun newThread(r: Runnable): Thread = Thread(r).apply {
            name = "$namePrefix-${UUID.randomUUID().toString().substringBefore('-')}"
            isDaemon = daemon
        }
    }

    private class CompletableAsync<R>(val future: CompletableFuture<R>) : Async<R> {
        companion object {
            fun <R> from(executor: Executor, block: () -> R) =
                CompletableAsync(CompletableFuture.supplyAsync(block, executor))
        }
    }
}

/**
 * An implementation of [ManageAsync] which evaluates the [Async] lazily when it is awaited. The same asyncs
 * can be re-awaited multiple times, but the same result will be returned.
 */
class ManageAsyncLazy : ManageAsync {
    override fun <R> async(waitForStart: Boolean, block: () -> R): Async<R> =
        LazyAsync(block)
    
    override fun <R> await(async: Async<R>, timeoutMillis: Long): Either<Async.Failure, R> {
        require(async is LazyAsync<R>)
        return when (val cancellation = async.cancellation) {
            null -> eitherCatching { async.value }.mapErr { Async.Failure.Unknown(it) }
            else -> err(Async.Failure.Cancelled(cancellation))
        }
    }

    override fun cancel(async: Async<*>, reason: String?) {
        require(async is LazyAsync<*>)
        async.cancellation = async.cancellation ?: CancellationException(reason ?: "Manually cancelled")
    }

    class LazyAsync<R>(block: () -> R) : Async<R> {
        val value: R by lazy { block() }
        @Volatile var cancellation: CancellationException? = null
    }
}