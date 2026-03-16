package hermetic.effects.fs

import hermetic.Err
import java.io.FileNotFoundException
import java.io.IOException

interface FsError {
    val path: Path
}

sealed interface GetError {
    val path: Path
}

sealed interface GetFileError {
    val path: Path
}

sealed interface GetDirError {
    val path: Path
}

sealed interface CreateError {
    val path: Path
}

sealed interface DeleteError {
    val path: Path
}

sealed interface FileError {
    val path: Path
}

data class ParentPathDoesNotExist(override val path: Path) : CreateError, DeleteError, Err() {
    override fun toString() = "ParentPathDoesNotExist(${path.java})"
}

data class AncestorPathIsFile(val ancestor: File, override val path: Path) : CreateError, Err() {
    override fun toString() = "AncestorPathIsFile(ancestor=$ancestor, path=$path)"
}

data class PermissionDenied(
    override val path: Path,
    override val cause: SecurityException
) : CreateError, DeleteError, FileError, Err() {
    override fun toString() = "PermissionDenied(${path.java})"
}

data class PathIsDir(val dir: Dir) : GetFileError, CreateError, Err() {
    override val path get() = dir.path
    override fun toString() = "PathIsDir(${dir.java})"
}

data class PathIsFile(val file: File) : GetDirError, CreateError, Err() {
    override val path get() = file.path
    override fun toString() = "PathIsFile(${file.java})"
}

data class PathDoesNotExist(
    override val path: Path,
    override val cause: FileNotFoundException?
) : GetError, GetFileError, GetDirError, DeleteError, FileError, Err() {
    override fun toString() = "PathDoesNotExist(${path.java})"
}

data class IOError(
    override val path: Path,
    override val cause: IOException
) : FileError, DeleteError, Err() {
    override fun toString() = "IOError(${path.java})"
}

data class PathStillExists(
    override val path: Path
) : DeleteError, Err() {
    override fun toString() = "PathStillExists(${path.java})"
}

class FinalizationError(val errors: List<Any>) : Err(errors) {
    override val message = "Failed to clean up all ephemeral resources"
}
