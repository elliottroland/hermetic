package hermetic.effects.fs

import hermetic.*
import hermetic.either.*
import hermetic.effects.*
import java.io.*
import java.nio.file.*
import java.nio.charset.*
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.io.*

/**
 * A [RestrictedFileSystem] which deletes all files created through it when closed.
 */
interface EphemeralFileSystem : RestrictedFileSystem, Finalizable<EphemeralFileSystem.FinalizationError> {
    class FinalizationError(val errors: List<DeleteError>) : hermetic.Err(errors) {
        override val message = "Failed to clean up all ephemeral resources"
    }
}

class DefaultEphemeralFileSystem(private val rfs: RestrictedFileSystem) : EphemeralFileSystem {
    private val toDelete = ConcurrentLinkedDeque<FileOrDir>()
    
    override fun rootDir(): Dir = rfs.rootDir()
    override fun ephemeral(): EphemeralFileSystem = this

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

fun main() {
    val gfs = GlobalFileSystem()
    val root = gfs.getOrCreateDir("efs-root").getOrThrow()
    gfs.restrictFs(root).ephemeral().use { efs ->
        context(efs, LoggingConsole()) {
            ephemeralProblem(gfs)
        }
    }.toEither().getOrThrow().also { println(it) }
}

context(fs: EphemeralFileSystem, _: Logging)
fun ephemeralProblem(gfs: GlobalFileSystem) = defers {
    val log = Log("effects.fs.ephemeralProblem")

    val dir = fs.createDir("some-dir").getOrThrow()
    // defer {
    //     log.info("Deleting dir in defer: $dir")
    //     fs.delete(dir).also { log.info(it) }.getOrThrow()
    // }

    gfs.createFile(dir.absolute, "persistent-file").getOrThrow()

    val file = fs.createFile(dir, "some-file").getOrThrow()
    // defer {
    //     log.info("Deleting file in defer: $file")
    //     fs.delete(file).also { log.info(it) }.getOrThrow()
    // }
}