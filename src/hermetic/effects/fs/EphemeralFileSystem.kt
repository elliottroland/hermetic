package hermetic.effects.fs

import hermetic.*
import hermetic.either.*
import hermetic.effects.*
import java.io.*
import java.nio.file.*
import java.nio.charset.*
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.io.*

class GlobalEphemeralFileSystem(private val rfs: GlobalFileSystem) : EphemeralFileSystem {
    private val toDelete = ConcurrentLinkedDeque<FileOrDir>()

    override fun get(path: Path) = rfs.get(path)
    override fun delete(ford: FileOrDir) = rfs.delete(ford)
    override fun inputStream(file: File) = rfs.inputStream(file)
    override fun outputStream(file: File) = rfs.outputStream(file)
    override fun walk(dir: Dir, maxDepth: Int, direction: FileWalkDirection, shouldEnter: (Dir) -> Boolean) =
        rfs.walk(dir, maxDepth, direction, shouldEnter)
    override fun restrictFs(rootDir: Dir): EphemeralFileSystem =
        rfs.restrictFs(rootDir).ephemeral()

    override fun createFile(path: Path, mkdirs: Boolean) =
        rfs.createFile(path, mkdirs).onOk { toDelete.addFirst(it) }

    override fun createTempFile(dir: Dir, prefix: String, suffix: String) =
        rfs.createTempFile(dir, prefix, suffix).onOk { toDelete.addFirst(it) }

    override fun createDir(path: Path, mkdirs: Boolean) =
        rfs.createDir(path, mkdirs).onOk { toDelete.addFirst(it) }

    override fun finalize(): EphemeralFileSystem.FinalizationError? {
        val errors = mutableListOf<DeleteError>()
        for (ford in toDelete) {
            delete(ford).onErr { errors.add(it.also { if (it is hermetic.Err) it.clearStackTrace() }) }
        }
        if (errors.isEmpty()) {
            return null
        }
        return EphemeralFileSystem.FinalizationError(errors)
    }
}
