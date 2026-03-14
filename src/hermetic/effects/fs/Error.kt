package hermetic.effects.fs

import hermetic.either.*
import java.io.*

sealed interface GetError {
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

data class ParentPathDoesNotExist(override val path: Path) : CreateError, DeleteError {
    override fun toString() = "ParentPathDoesNotExist(${path.java})"
}

data class PermissionDenied(
    override val path: Path,
    override val cause: SecurityException
) : GetError, CreateError, DeleteError, FileError, Exception() {
    override fun toString() = "PermissionDenied(${path.java})"
}

data class PathIsDir(val dir: Dir) : GetError, CreateError {
    override val path get() = dir.path
    override fun toString() = "PathIsDir(${dir.java})"
}

data class PathIsFile(val file: File) : GetError, CreateError {
    override val path get() = file.path
    override fun toString() = "PathIsFile(${file.java})"
}

data class PathDoesNotExist(
    override val path: Path,
    override val cause: FileNotFoundException?
) : GetError, DeleteError, FileError, Exception() {
    override fun toString() = "PathDoesNotExist(${path.java})"
}

data class IOError(
    override val path: Path,
    override val cause: IOException
) : FileError, DeleteError, Exception() {
    override fun toString() = "IOError(${path.java})"
}

data class PathStillExists(
    override val path: Path
) : DeleteError, Exception() {
    override fun toString() = "PathStillExists(${path.java})"
}