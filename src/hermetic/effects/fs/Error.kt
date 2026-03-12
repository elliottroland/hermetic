package hermetic.effects.fs

import hermetic.either.*
import java.io.*

sealed interface GetError
sealed interface CreateError
sealed interface DeleteError
sealed interface FileError

data class ParentPathDoesNotExist(val path: Path) : CreateError, DeleteError {
    override fun toString() = "ParentPathDoesNotExist(${path.java})"
}

data class PermissionDenied(
    val path: Path,
    override val exception: SecurityException
) : GetError, CreateError, DeleteError, FileError, Exceptional {
    override fun toString() = "PermissionDenied(${path.java}, $exception)"
}

data class PathIsDir(val dir: Dir) : GetError, CreateError {
    override fun toString() = "PathIsDir(${dir.java})"
}

data class PathIsFile(val file: File) : GetError, CreateError {
    override fun toString() = "PathIsFile(${file.java})"
}

data class PathDoesNotExist(
    val path: Path,
    override val exception: FileNotFoundException?
) : GetError, DeleteError, FileError, Exceptional {
    override fun toString() = "PathDoesNotExist(${path.java}${ if (exception != null) ", $exception" else "" })"
}

data class IOError(
    val path: Path,
    override val exception: IOException
) : FileError, DeleteError, Exceptional {
    override fun toString() = "IOError(${path.java}, $exception)"
}