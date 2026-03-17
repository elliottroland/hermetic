package hermetic.exercises

import hermetic.either.Either
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

data class AncestorPathIsFile(val ancestor: File, override val path: Path) : CreateError, Exception() {
    override fun toString() = "AncestorPathIsFile(${path.java}, ancestor=$ancestor)"
}

data class ParentPathDoesNotExist(override val path: Path) : CreateError, DeleteError, Exception() {
    override fun toString() = "ParentPathDoesNotExist(${path.java})"
}

data class PermissionDenied(
    override val path: Path,
    override val cause: SecurityException
) : GetError, CreateError, DeleteError, FileError, Exception() {
    override fun toString() = "PermissionDenied(${path.java})"
}

data class PathIsDir(val dir: Dir) : GetError, CreateError, Exception() {
    override val path get() = dir.path
    override fun toString() = "PathIsDir(${dir.java})"
}

data class PathIsFile(val file: File) : GetError, CreateError, Exception() {
    override val path get() = file.path
    override fun toString() = "PathIsFile(${file.java})"
}

data class PathDoesNotExist(
    override val path: Path
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