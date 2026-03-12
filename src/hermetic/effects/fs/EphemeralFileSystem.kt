package hermetic.effects.fs

import hermetic.either.*
import hermetic.effects.Async
import java.io.*
import java.nio.file.*
import java.nio.charset.*
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.io.*

/**
 * A [RestrictedFileSystem] which deletes all files created through it when closed.
 */
class EphemeralFileSystem(private val rfs: RestrictedFileSystem) : RestrictedFileSystem by rfs, Closeable {
    private val toDelete = ConcurrentLinkedDeque<FileOrDir>()

    override fun createFile(path: Path, mkdirs: Boolean): Either<CreateError, File> =
        rfs.createFile(path, mkdirs).onOk { toDelete.addFirst(it) }

    override fun createTempFile(dir: Dir, prefix: String, suffix: String): Either<CreateError, File> =
        rfs.createTempFile(dir, prefix, suffix).onOk { toDelete.addFirst(it) }

    override fun createDir(path: Path, mkdirs: Boolean): Either<CreateError, Dir> =
        rfs.createDir(path, mkdirs).onOk { toDelete.addFirst(it) }

    override fun close() {
        val errors = mutableList<DeleteError>()
        for (ford in toDelete) {
            delete(ford).onErr { errors.add(it) }
        }
        if (errors.isNotEmpty()) {
            throw IllegalStateException()
        }
    }
}