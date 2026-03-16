package hermetic

import hermetic.effects.*
import hermetic.effects.fs.*
import hermetic.either.*
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.io.writer

/**
 * Executes the [block] within a [DeferScope]. Generally, it is recommended that you use the [either] for handling
 * deferring code, but if you don't need the extra machinery around error handling then this is a much simpler
 * alternative. The value passed to the each [defer][DeferScope] call will be the computed return value if the [block]
 * exitted normally, or null if it ended exceptionally. If the [block] or any of the [defer][DeferScope] calls end
 * exceptionally, the first observed exception is thrown.
 * 
 * @see DeferScope.defer
 */
inline fun <R> defers(block: DeferScope<EmptyDeferBlockScope>.() -> R): R {
    val scope = DeferScope<EmptyDeferBlockScope>()
    var result: R? = null
    var succeeded = false
    try {
        result = scope.block()
        succeeded = true
        return result
    } finally {
        if (scope.hasDeferrals()) {
            var deferError: Exception? = null
            for (deferral in scope.deferrals) {
                try {
                    EmptyDeferBlockScope.deferral()
                } catch (e: Exception) {
                    if (deferError == null && succeeded) {
                        deferError = e
                    }
                }
            }
            deferError?.also { throw it }
        }
    }
}

/**
 * Defines a scope which exposes various functions for deferring computations until the end of the scope, whether it
 * end with a success or failure.
 */
@DeferMarker
open class DeferScope<BlockScope : DeferBlockScope> {
    private val lazyDeferrals = lazy(LazyThreadSafetyMode.PUBLICATION) {
        ConcurrentLinkedDeque<BlockScope.() -> Unit>()
    }
    @PublishedApi internal val deferrals by lazyDeferrals

    @PublishedApi internal fun hasDeferrals(): Boolean = lazyDeferrals.isInitialized()

    /**
     * Defer execution of the [block] until the end of the [either] scope, regardless of whether the
     * latter ended successfully. Defer blocks are executed in reverse order from their definitions, to
     * allow for the proper cleanup of resources.
     */
    fun defer(block: BlockScope.() -> Unit) {
        deferrals.addFirst(block)
    }

    /**
     * Like [defer], except any exceptions thrown by the [block] are caught and ignored.
     */
    fun deferCatching(block: BlockScope.() -> Unit) =
        defer { runCatching(block) }

    /**
     * Defers calling [close]. If the call throws an exception, then [onException] will be called in
     * order to provide an opportunity for handling exceptions. If [onException] is not specified, then
     * any exceptions encountered are thrown.
     *
     * This is defined on the closeable so that you can easily defer its closure as soon as possible
     * after creation:
     * ```
     * val writer = file.writer().getOrThrow().deferClose()
     * ```
     */
    fun <T : AutoCloseable> T.deferClose(onException: (Exception) -> Unit = { throw it }): T =
        apply {
            defer {
                try {
                    close()
                } catch (e: Exception) {
                    onException(e)
                }
            }
        }

    /**
     * Defers calling [finalize]. If the call ends with an error, then by default it will be thrown as a [throwable]. If
     * [onError] is provided, then that will be run instead.
     */
    fun <E, F : Finalizable<E>> F.deferFinalize(onError: (E & Any) -> Unit = { throw throwable(it) }): F =
        apply {
            defer {
                finalize()?.also { onError(it) }
            }
        }
}

@DeferMarker
interface DeferBlockScope

object EmptyDeferBlockScope : DeferBlockScope

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class DeferMarker

fun main() {
    val fs = GlobalFileSystem(null)
    val root = fs.getOrCreateDir("deferTest").getOrThrow()
    context(fs.restrictFs(root), LoggingConsole()) {
        println(testDefer().getOrThrow())
    }
}

context(fs: FileSystem, _: Logging)
fun testDefer(): Either<String, Long> = either {
    val log = Log("testDefer")

    val dir = fs.getOrCreateDir("some-dir").getOrFail { "Failed to create dir: $it" }
    log.info("Created dir: $dir")
    defer {
        log.info("Deleting dir: $dir")
        fs.delete(dir).getOrFail { "Failed to clean up dir: $it" }
        // defer {
        //     println("Nested defer is executing")
        // }
    }

    val file = fs.createFile(dir.resolve("some-file")).getOrFail { "Failed to create file: $it" }
    log.info("Created file: $file")
    defer {
        log.info("Deleting file: $file")
        fs.delete(file).getOrFail { "Failed to clean up file: $it" }
    }

    val writer = file.writer().getOrFail { "Could not create writer: $it" }
    writer.use { it.write("Hello, world!") }

    log.info("File contents: ${file.reader().getOrThrow().readLines()}")
    file.sizeBytes
}



