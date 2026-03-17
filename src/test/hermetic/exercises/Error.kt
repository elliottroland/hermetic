package hermetic.exercises

import hermetic.either.Either
import java.io.*

sealed interface GetError : GetFileError, GetDirError

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

data class ParentPathDoesNotExist(override val path: Path) : CreateError, DeleteError, Exception() {
    override fun toString() = "ParentPathDoesNotExist(${path.java})"
}

data class AncestorPathIsFile(val ancestor: File, override val path: Path) : CreateError, Exception() {
    override fun toString() = "AncestorPathIsFile(ancestor=$ancestor, path=$path)"
}

data class PermissionDenied(
    override val path: Path,
    override val cause: SecurityException
) : CreateError, DeleteError, FileError, Exception() {
    override fun toString() = "PermissionDenied(${path.java})"
}

data class PathIsDir(val dir: Dir) : GetFileError, CreateError, Exception() {
    override val path get() = dir.path
    override fun toString() = "PathIsDir(${dir.java})"
}

data class PathIsFile(val file: File) : GetDirError, CreateError, Exception() {
    override val path get() = file.path
    override fun toString() = "PathIsFile(${file.java})"
}

data class PathDoesNotExist(
    override val path: Path
) : GetError, GetFileError, GetDirError, DeleteError, FileError, Exception() {
    override fun toString() = "PathDoesNotExist(${path.java})"
}

data class PathStillExists(
    override val path: Path
) : DeleteError, Exception() {
    override fun toString() = "PathStillExists(${path.java})"
}