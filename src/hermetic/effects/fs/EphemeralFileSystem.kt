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
interface EphemeralFileSystem : RestrictedFileSystem, Finalizable<EphemeralFinalizeError>

data class EphemeralFinalizeError(val errors: List<DeleteError>, override val exception: FinalizationException) : Exceptional

class DefaultEphemeralFileSystem(private val rfs: RestrictedFileSystem) : EphemeralFileSystem {
    private val toDelete = ConcurrentLinkedDeque<FileOrDir>()
    
    override fun rootDir(): Dir = rfs.rootDir()
    override fun ephemeral(): EphemeralFileSystem = this

    override fun get(path: Path): Either<GetError, FileOrDir> = rfs.get(path)
    override fun delete(ford: FileOrDir): Either<DeleteError, Boolean> = rfs.delete(ford)
    override fun inputStream(file: File): Either<FileError, InputStream> = rfs.inputStream(file)
    override fun outputStream(file: File): Either<FileError, OutputStream> = rfs.outputStream(file)
    override fun walk(dir: Dir, maxDepth: Int, direction: FileWalkDirection, shouldEnter: (Dir) -> Boolean): Sequence<FileOrDir> = rfs.walk(dir, maxDepth, direction, shouldEnter)
    override fun restrictFs(rootDir: Dir): EphemeralFileSystem = rfs.restrictFs(rootDir).ephemeral()

    override fun createFile(path: Path, mkdirs: Boolean): Either<CreateError, File> =
        rfs.createFile(path, mkdirs).onOk { toDelete.addFirst(it) }

    override fun createTempFile(dir: Dir, prefix: String, suffix: String): Either<CreateError, File> =
        rfs.createTempFile(dir, prefix, suffix).onOk { toDelete.addFirst(it) }

    override fun createDir(path: Path, mkdirs: Boolean): Either<CreateError, Dir> =
        rfs.createDir(path, mkdirs).onOk { toDelete.addFirst(it) }

    override fun finalize(): EphemeralFinalizeError? {
        val errors = mutableListOf<DeleteError>()
        for (ford in toDelete) {
            println("Deleting $ford")
            delete(ford).also { println("Result: $it") }.onErr { errors.add(it) }
        }
        if (errors.isEmpty()) {
            return null
        }
        val exception = FinalizationException("Ephemeral file system could not clean up all resources", errors)
        return EphemeralFinalizeError(errors, exception)
    }
}

fun main() {
    val gfs = GlobalFileSystem()
    val root = gfs.createDir("my-test-dir").getOrThrow()
    val efs = gfs.restrictFs(root).ephemeral()
    val dir = efs.createDir("some-dir").getOrThrow()
    val file = efs.createFile(dir, "some-file").getOrThrow()
    gfs.createFile(dir.path.absolute.resolve("some-other-file")).getOrThrow()
    println(efs.finalize()?.also { throw it.exception })
}