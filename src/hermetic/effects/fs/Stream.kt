package hermetic.effects.fs

import hermetic.effects.Async
import java.io.*

/**
 * A [FilterInputStream] which offloads all writes to the underlying stream to the provided [Async].
 */
class AsyncInputStream(inner: InputStream, private val async: Async) : FilterInputStream(inner) {
    // TODO Read
}

/**
 * A [FilterInputStream] which offloads all writes to the underlying stream to the provided [Async].
 */
class AsyncOutputStream(inner: OutputStream, private val async: Async) : FilterOutputStream(inner) {
    // TODO Write

    override fun flush() {
        async.await(async.async(waitForStart = false) { super.flush() }, timeoutMillis = Long.MAX_VALUE)
    }

    override fun close() {
        async.await(async.async(waitForStart = false) { super.close() }, timeoutMillis = Long.MAX_VALUE)
    }
}