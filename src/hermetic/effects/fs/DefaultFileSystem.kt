package hermetic.effects.fs

import hermetic.effects.Async
import hermetic.either.Either
import hermetic.either.err
import hermetic.either.ok
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * The default implementation used for [FileSystem]s. It is not recommended that you construct this directly, but
 * rather use the builders defined on the [FileSystem] type itself.
 */
class DefaultFileSystem<out L : Lifespan, out S : Scope>(
    override val lifespan: L,
    override val scope: S,
    private val async: Async?
) : FileSystem<L, S> {
    override fun get(path: Path): Either<GetError, FileOrDir> {
        val file = scope.resolve(path).java.toFile()
        return when {
            !file.exists() -> err(PathDoesNotExist(path, null))
            else -> ok(file.toFileOrDir())
        }
    }

    override fun createFile(path: Path, mkdirs: Boolean): Either<CreateError, File> =
        try {
            val file = scope.resolve(path).java.toFile()
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
        }.onOk { lifespan.register(it) }

    override fun createTempFile(dir: Dir, prefix: String, suffix: String): Either<CreateError, File> =
        try {
            ok(File(java.io.File.createTempFile(prefix, suffix, scope.resolve(dir).java)))
        } catch (e: SecurityException) {
            err(PermissionDenied(dir.path, e))
        } catch (e: IOException) {
            // TODO: Is this the right interpretation?
            err(ParentPathDoesNotExist(dir.path))
        }.onOk { lifespan.register(it) }

    override fun createDir(path: Path, mkdirs: Boolean): Either<CreateError, Dir> =
        try {
            val resolved = scope.resolve(path).java.toFile()
            if (mkdirs) {
                resolved.parentFile.mkdirs()
            }
            when {
                resolved.mkdir() -> ok(Dir(resolved))
                resolved.isFile() -> err(PathIsFile(File(resolved)))
                else -> err(PathIsDir(Dir(resolved)))
            }
        } catch (e: SecurityException) {
            err(PermissionDenied(path, e))
        } catch (e: IOException) {
            // TODO: Is this the right interpretation?
            err(ParentPathDoesNotExist(path))
        }.onOk { lifespan.register(it) }

    override fun delete(ford: FileOrDir): Either<DeleteError, Boolean> =
        try {
            val resolved = scope.resolve(ford)
            when {
                !resolved.java.exists() -> ok(false)
                !resolved.java.delete() -> err(PathStillExists(ford.path))
                else -> ok(true)
            }
        } catch (e: SecurityException) {
            err(PermissionDenied(ford.path, e))
        } catch (e: IOException) {
            err(IOError(ford.path, e))
        }

    override fun inputStream(file: File): Either<FileError, InputStream> =
        try {
            val inputStream = FileInputStream(scope.resolve(file).java)
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
            val outputStream = FileOutputStream(scope.resolve(file).java)
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
        scope.resolve(dir).java.walk(direction)
            .maxDepth(maxDepth)
            .onEnter { shouldEnter(Dir(it)) }
            .map { it.toFileOrDir() }
    
    override fun restricted(rootDir: Dir): FileSystem<L, Scope.Restricted> =
        DefaultFileSystem(lifespan, Scope.Restricted(scope.resolve(rootDir)), async)

    override fun ephemeral(): FileSystem<Lifespan.Ephemeral, S> =
        DefaultFileSystem(Lifespan.Ephemeral(), scope, async)

    private fun java.io.File.toFileOrDir(): FileOrDir =
        when {
            isFile() -> File(this)
            else -> Dir(this)
        }
}
