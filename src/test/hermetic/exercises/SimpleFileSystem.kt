package hermetic.exercises

import hermetic.either.*
import java.io.*

class SimpleFileSystem {
    /**
     * - Returns ok([File]) or ok([Dir]) at the target [path] if it exists.
     * - Returns err([PathDoesNotExist]) if the target [path] does not point to anything.
     * - Returns err([PermissionDenied]) if there is an issue accessing the target.
     */
    fun get(path: Path): Either<GetError, FileOrDir> {
        val file = path.java.toFile()
        return when {
            !file.exists() -> err(PathDoesNotExist(path))
            file.isDirectory() -> ok(Dir(file))
            else -> ok(File(file))
        }
    }

    /**
     * Returns ok([File]) at the given [path] if it exists and is a file. In addition to the
     * [err]s which [get] may return, this also returns err([PathIsDir]) if the target exists
     * but is a dir.
     */
    fun getFile(path: Path): Either<GetFileError, File> = //TODO
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
    fun getDir(path: Path): Either<GetDirError, Dir> = //TODO
        get(path).flatMap {
            when (it) {
                is File -> err(PathIsFile(it))
                is Dir -> ok(it)
            }
        }

    /**
     * Creates a new [Dir] at the given [path], including parents if [mkdirs] is true. Fails if the
     * directory cannot be created, including if there already exists a directory at the target.
     * 
     * - Returns err([ParentPathDoesNotExist]) if the parent dir does not exist, and mkdirs is not true.
     * - Returns err([AncestorPathIsFile]) if one of the ancestors of the target is a file.
     * - Returns err([PermissionDenied]) if a SecurityException is encountered.
     * - Returns err([PathIsDir]) if the target already exists and is a dir.
     * - Returns err([PathIsFile]) if the target already exists and is a file.
     */
    fun createDir(path: Path, mkdirs: Boolean = false): Either<CreateError, Dir> {
        val resolved = path.normalized

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

        return try {
            // Attempt to create the file -- and fail hard if we can't for some reason
            val file = resolved.java.toFile()
            check(file.mkdir()) { "Failed to create directory at $file" }
            ok(Dir(file))
        } catch (e: SecurityException) {
            err(PermissionDenied(path, e))
        }
    }

    /**
     * Create a new [File] at the given [path], including parent directories if [mkdirs] is true. Fails
     * if the file cannot be create, including if there already exists a file at the target.
     *
     * - Returns err([ParentPathDoesNotExist]) if the parent dir does not exist, and mkdirs is not true.
     * - Returns err([AncestorPathIsFile]) if one of the ancestors of the target is a file.
     * - Returns err([PermissionDenied]) if a SecurityException is encountered.
     * - Returns err([PathIsDir]) if the target already exists and is a dir.
     * - Returns err([PathIsFile]) if the target already exists and is a file.
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

    /**
     * Deletes the given [FileOrDir], if it still exists, returning an error if this is not possible
     * or the file or dir no longer exists.
     *
     * - Returns err([PermissionDenied]) if a SecurityException is encountered.
     * - Returns err([PathDoesNotExist]) if [ford] no longer refers to an existing file or dir.
     * - Returns err([PathStillExists]) if, after attempting an delete, the target still exists. This is
     *   most likely due to 
     */
    fun delete(ford: FileOrDir): Either<DeleteError, Unit> = TODO()

    /**
     * Attempts to delete the target at the given path, and returns whether anything was there to be deleted.
     * 
     * - Returns err([PermissionDenied]) if a SecurityException is encountered.
     * - Returns err([PathStillExists]) if, after attempting an delete, the target still exists. This is
     *   most likely due to 
     */
    fun deleteIfExists(path: Path): Either<DeleteError, Boolean> = TODO()

    /**
     * Returns unit if the target path does not exist, otherwise returns a [CreateError] encoding
     * how it exists: a [PathIsFile] or a [PathIsDir].
     */
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

    /**
     * Returns the list of paths from the root up the parent of the current path.
     * ```
     * Path.of("/this/is/the/path").ancestors()
     * [
     *   Path("/"),
     *   Path("/this"),
     *   Path("/this/is"),
     *   Path("/this/is/the")
     * ]
     * ```
     */
    private fun Path.ancestors(): List<Path> = buildList {
        var path = this@ancestors
        while (path.hasParent) {
            add(0, path.parent)
            path = path.parent
        }
    }
}