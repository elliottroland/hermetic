package hermetic.exercises

import hermetic.either.*
import java.io.*

class SimpleFileSystem {
    /**
     * Returns ok([File]) or ok([Dir]) at the target [path] if it exists.
     * Returns err([PathDoesNotExist]) if the target [path] does not point to anything.
     * Returns err([PermissionDenied]) if there is an issue accessing the target.
     */
    fun get(path: Path): Either<GetError, FileOrDir> =
        try {
            val file = path.java.toFile()
            when {
                !file.exists() -> err(PathDoesNotExist(path))
                file.isDirectory() -> ok(Dir(file))
                else -> ok(File(file))
            }
        } catch (e: SecurityException) {
            return err(PermissionDenied(path, e))
        }

    /**
     * Returns ok([File]) at the given [path] if it exists and is a file. In addition to the
     * [err]s which [get] may return, this also returns err([PathIsDir]) if the target exists
     * but is a dir.
     */
    fun getFile(path: Path): Either<GetError, File> = //TODO
        get(path).flatMap {
            when (it) {
                is File -> ok(it)
                is Dir -> err(PathIsDir(it))
            }
        }

    /**
     * Returns ok([Dir]) at the given [path] if it exists and is a dir. In addition to the
     * [err]s which [get] may return, this also returns err([PathIsFile]) if the target exists
     * but is a file.
     */
    fun getDir(path: Path): Either<GetError, Dir> = //TODO
        get(path).flatMap {
            when (it) {
                is File -> err(PathIsFile(it))
                is Dir -> ok(it)
            }
        }

    /**
     * Creates a new [Dir] at the given [path], including parents if [mkdirs] is true. Fails if the
     * directory cannot be created, including if there already exists a directory at the target.
     */
    fun createDir(path: Path, mkdirs: Boolean = false): Either<CreateError, Dir> = TODO()

    /**
     * Create a new [File] at the given [path], including parent directories if [mkdirs] is true. Fails
     * if the file cannot be create, including if there already exists a file at the target.
     */
    fun createFile(path: Path, mkdirs: Boolean = false): Either<CreateError, File> = TODO()

    /**
     * Gets the already-existing [File] at the given [path], or creates one if possible.
     */
    fun getOrCreateFile(path: Path, mkdirs: Boolean = false): Either<CreateError, File> = TODO()

    /**
     * Gets the already-existing [Dir] at the given [path], or creates one if possible.
     */
    fun getOrCreateDir(path: Path, mkdirs: Boolean = false): Either<CreateError, File> = TODO()
}