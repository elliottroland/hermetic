package hermetic.effects.fs

import hermetic.either.*
import hermetic.effects.Async
import java.io.*
import java.nio.file.*
import java.nio.charset.*
import kotlin.io.*

/**
 * A [FileSystem] which is not scoped to any particular root directory. Generally, this should
 * not be used, and rather a [RestrictedFileSystem] or [EphemeralFileSystem] should be used instead.
 * 
 * If this file system is created with an [async], then all [inputStream]s and [outputStream]s are
 * wrapped in asynchronous versions, offloading all disk IO to a dedicated async pool. This is the
 * recommended mechanism for disk IO, since it allows these operations to be scaled to the needs of
 * the underlying host independently of the workload of your application code.
 */
class GlobalFileSystem(private val async: Async? = null) : FileSystem {
    override fun get(path: Path): Either<GetError, FileOrDir> {
        val file = path.java.toFile()
        return when {
            !file.exists() -> err(PathDoesNotExist(path, null))
            // TODO: PermissionDenied?
            else -> ok(file.toFileOrDir())
        }
    }

    override fun createFile(path: Path, mkdirs: Boolean): Either<CreateError, File> =
        try {
            val file = path.java.toFile()
            if (mkdirs) {
                file.parentFile.mkdirs()
            }
            when {
                file.createNewFile() -> ok(File(file))
                file.isDirectory() -> err(PathIsDir(Dir(file)))
                else -> err(PathIsFile(File(file)))
            }
        } catch (e: SecurityException) {
            err(PermissionDenied(path, e))
        } catch (e: IOException) {
            // TODO: Is this the right interpretation?
            err(ParentPathDoesNotExist(path))
        }

    override fun createTempFile(dir: Dir, prefix: String, suffix: String): Either<CreateError, File> =
        try {
            ok(File(java.io.File.createTempFile(prefix, suffix, dir.java)))
        } catch (e: SecurityException) {
            err(PermissionDenied(dir.path, e))
        } catch (e: IOException) {
            // TODO: Is this the right interpretation?
            err(ParentPathDoesNotExist(dir.path))
        }

    override fun createDir(path: Path, mkdirs: Boolean): Either<CreateError, Dir> =
        try {
            val file = path.java.toFile()
            if (mkdirs) {
                file.parentFile.mkdirs()
            }
            when {
                file.mkdir() -> ok(Dir(file))
                file.isFile() -> err(PathIsFile(File(file)))
                else -> err(PathIsDir(Dir(file)))
            }
        } catch (e: SecurityException) {
            err(PermissionDenied(path, e))
        } catch (e: IOException) {
            // TODO: Is this the right interpretation?
            err(ParentPathDoesNotExist(path))
        }

    override fun delete(ford: FileOrDir): Either<DeleteError, Boolean> =
        try {
            when {
                !ford.java.exists() -> ok(false)
                !ford.java.delete() -> err(PathStillExists(ford.path))
                else -> ok(true)
            }
        } catch (e: SecurityException) {
            err(PermissionDenied(ford.path, e))
        } catch (e: IOException) {
            err(IOError(ford.path, e))
        }

    override fun inputStream(file: File): Either<FileError, InputStream> =
        try {
            val inputStream = FileInputStream(file.java)
            when {
                async != null -> ok(AsyncInputStream(inputStream, async))
                else -> ok(inputStream)
            }
        } catch (e: SecurityException) {
            err(PermissionDenied(file.path, e))
        } catch (e: FileNotFoundException) {
            err(PathDoesNotExist(file.path, e))
        }

    override fun outputStream(file: File): Either<FileError, OutputStream> =
        try {
            val outputStream = FileOutputStream(file.java)
            when {
                async != null -> ok(AsyncOutputStream(outputStream, async))
                else -> ok(outputStream)
            }
        } catch (e: SecurityException) {
            err(PermissionDenied(file.path, e))
        } catch (e: FileNotFoundException) {
            err(PathDoesNotExist(file.path, e))
        }

    override fun walk(dir: Dir, maxDepth: Int, direction: FileWalkDirection, shouldEnter: (Dir) -> Boolean): Sequence<FileOrDir> =
        dir.java.walk(direction)
            .maxDepth(maxDepth)
            .onEnter { shouldEnter(Dir(it)) }
            .map { it.toFileOrDir() }
    
    override fun restrictFs(rootDir: Dir): RestrictedFileSystem =
        DefaultRestrictedFileSystem(rootDir, this)

    private fun java.io.File.toFileOrDir(): FileOrDir =
        when {
            isFile() -> File(this)
            else -> Dir(this)
        }
}