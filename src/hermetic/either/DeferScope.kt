package hermetic.either

import hermetic.effects.*
import hermetic.effects.fs.*
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
inline fun <R> defers(block: DeferScope<R?, EmptyDeferBlockScope>.() -> R): R {
    val scope = DeferScope<R?, EmptyDeferBlockScope>()
    var result: R? = null
    try {
        result = scope.block()
        return result
    } finally {
        if (scope.hasDeferrals()) {
            var deferError: Exception? = null
            for (deferral in scope.deferrals) {
                try {
                    EmptyDeferBlockScope.deferral(result)
                } catch (e: Exception) {
                    if (deferError == null && result == null) {
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
open class DeferScope<R, BlockScope : DeferBlockScope> {
    private val lazyDeferrals = lazy(LazyThreadSafetyMode.PUBLICATION) {
        ConcurrentLinkedDeque<BlockScope.(R) -> Unit>()
    }
    @PublishedApi internal val deferrals by lazyDeferrals

    @PublishedApi internal fun hasDeferrals(): Boolean = lazyDeferrals.isInitialized()

    /**
     * Defer execution of the [block] until the end of the [either] scope, regardless of whether the
     * latter ended successfully. Defer blocks are executed in reverse order from their definitions, to
     * allow for the proper cleanup of resources.
     */
    fun defer(block: BlockScope.(R) -> Unit) {
        deferrals.addFirst(block)
    }

    /**
     * Defers calling [close]. If the call throws an exception, then [onException] will be called in
     * order to provide an opportunity for mapping the exception to a modelled error. If the
     * [onException] block is not provided, then the exception is thrown as normal. As with normal defer
     * calls, you may use the [EitherScope]'s failure methods for modeling failures and it is passed the
     * current response of the block.
     *
     * This is defined on the closeable so that you can easily defer its closure as soon as possible
     * after creation:
     * ```
     * val writer = file.writer().getOrFail().deferClose()
     * ```
     *
     * This function be thought of as a generalization of [kotlin.io.use], in that it supports the
     * automatic closure of [AutoCloseable]s, but in a way which is compatible with [Either] semantics.
     */
    fun <T : AutoCloseable> T.deferClose(onException: (R, Exception) -> Unit = { _, ex -> throw ex }): T =
        apply {
            defer { response ->
                try {
                    close()
                } catch (e: Exception) {
                    onException(response, e)
                }
            }
        }

    fun <E, F : Finalizable<E>> F.deferFinalize(onError: (R, E) -> Unit): F =
        apply {
            defer { response ->
                finalize()?.also { onError(response, it) }
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



