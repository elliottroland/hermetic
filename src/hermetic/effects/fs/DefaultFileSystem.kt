package hermetic.effects.fs

import hermetic.effects.Async
import hermetic.either.Either
import hermetic.either.err
import hermetic.either.getOr
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
            val resolved = scope.resolve(path)

            // Ensure we're about to create something
            checkDoesNotExist(resolved).onErr { return err(it) }

            // Ensure the directory for the file exists
            getDir(resolved.parent).onErr { err ->
                when (err) {
                    is PathDoesNotExist -> when {
                        mkdirs -> createDir(resolved.parent, mkdirs = true).getOr { return err(it) }
                        else -> return err(ParentPathDoesNotExist(resolved))
                    }
                    is PathIsFile -> return err(AncestorPathIsFile(err.file, resolved))
                }
            }

            // Create the file and fail hard if we can't
            val file = resolved.java.toFile()
            check(file.createNewFile()) { "Failed to create new file at $file" }
            ok(File(file))
        } catch (e: SecurityException) {
            err(PermissionDenied(path, e))
        } catch (e: IOException) {
            // TODO: Is this the right interpretation?
            err(ParentPathDoesNotExist(path))
        }.onOk { lifespan.register(it) }

    override fun createDir(path: Path, mkdirs: Boolean): Either<CreateError, Dir> {
        return try {
            val resolved = scope.resolve(path).normalized

            // Ensure we're about to create something
            checkDoesNotExist(resolved).onErr { return err(it) }

            // Ensure all ancestors exist, and none are files
            getDir(resolved.parent).onErr {
                for (ancestor in resolved.ancestors()) {
                    getDir(ancestor).onErr { err ->
                        when (err) {
                            is PathDoesNotExist -> when {
                                mkdirs -> createDir(ancestor, mkdirs = false).onErr { return err(ParentPathDoesNotExist(resolved)) }
                                else -> return err(ParentPathDoesNotExist(resolved))
                            }
                            is PathIsFile -> return err(AncestorPathIsFile(err.file, path))
                        }
                    }
                }
            }

            // Attempt to create the file -- and fail hard if we can't for some reason
            val file = resolved.java.toFile()
            check(file.mkdir()) { "Failed to create directory at $file" }
            ok(Dir(file))
        } catch (e: SecurityException) {
            err(PermissionDenied(path, e))
        } catch (e: IOException) {
            // TODO: Is this the right interpretation?
            err(ParentPathDoesNotExist(path))
        }.onOk { lifespan.register(it) }
    }

    override fun createTempFile(dir: Dir, prefix: String, suffix: String): Either<CreateError, File> =
        try {
            val resolved = scope.resolve(dir)
            getDir(resolved.path).onErr {
                return when (it) {
                    is PathIsFile -> err(AncestorPathIsFile(it.file, resolved.path))
                    is PathDoesNotExist -> err(ParentPathDoesNotExist(resolved.path))
                }
            }
            ok(File(java.io.File.createTempFile(prefix, suffix, resolved.java)))
        } catch (e: SecurityException) {
            err(PermissionDenied(dir.path, e))
        } catch (e: IOException) {
            // TODO: Is this the right interpretation?
            err(ParentPathDoesNotExist(dir.path))
        }.onOk { lifespan.register(it) }

    override fun delete(ford: FileOrDir): Either<DeleteError, Unit> =
        try {
            val resolved = scope.resolve(ford)
            when {
                !resolved.java.exists() -> err(PathDoesNotExist(resolved.path, null))
                !resolved.java.delete() -> err(PathStillExists(ford.path))
                else -> ok(Unit)
            }
        } catch (e: SecurityException) {
            err(PermissionDenied(ford.path, e))
        } catch (e: IOException) {
            err(IOError(ford.path, e))
        }

    override fun inputStream(file: File): Either<FileError, InputStream> =
        try {
            val resolved = scope.resolve(file).java
                .takeIf { it.exists() }
                ?: return err(PathDoesNotExist(file.path, null))
            val inputStream = FileInputStream(resolved)
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
            val resolved = scope.resolve(file).java
                .takeIf { it.exists() }
                ?: return err(PathDoesNotExist(file.path, null))
            val outputStream = FileOutputStream(resolved)
            when {
                async != null -> ok(AsyncOutputStream(outputStream, async))
                else -> ok(outputStream)
            }
        } catch (e: SecurityException) {
            err(PermissionDenied(file.path, e))
        } catch (e: FileNotFoundException) {
            err(PathDoesNotExist(file.path, e))
        }

    override fun walk(dir: Dir, maxDepth: Int, shouldEnter: (Dir) -> Boolean): Sequence<FileOrDir> =
        scope.resolve(dir).java
            .walk(FileWalkDirection.TOP_DOWN)
            .maxDepth(maxDepth)
            .onEnter { shouldEnter(Dir(it)) }
            .map { it.toFileOrDir() }
    
    override fun restricted(rootDir: Dir): FileSystem<L, Scope.Restricted> =
        DefaultFileSystem(lifespan, Scope.Restricted(scope.resolve(rootDir)), async)

    override fun ephemeral(): FileSystem<Lifespan.Ephemeral, S> =
        DefaultFileSystem(Lifespan.Ephemeral(), scope, async)

    override fun async(async: Async?): FileSystem<L, S> =
        DefaultFileSystem(lifespan, scope, async)

    private fun java.io.File.toFileOrDir(): FileOrDir =
        when {
            isFile() -> File(this)
            else -> Dir(this)
        }

    private fun checkDoesNotExist(resolved: Path): Either<CreateError, Unit> {
        get(resolved).onOk {
            // If the path already exists then we can't create it, and return as much
            return when (it) {
                is File -> err(PathIsFile(it))
                is Dir -> err(PathIsDir(it))
            }
        }.onErr {
            // We include this branch to be sure that no new cases are added without this function being updated
            when (it) {
                is PathDoesNotExist -> Unit // Good
            }
        }
        return ok(Unit)
    }

    private fun Path.ancestors(): List<Path> = buildList {
        var path = this@ancestors
        while (path.hasParent) {
            add(0, path.parent)
            path = path.parent
        }
    }
}
