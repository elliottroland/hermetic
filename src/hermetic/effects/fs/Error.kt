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
    override val exception: SecurityException
) : GetError, CreateError, DeleteError, FileError, Exceptional {
    override fun toString() = "PermissionDenied(${path.java}, $exception)"
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
    override val exception: FileNotFoundException?
) : GetError, DeleteError, FileError, Exceptional {
    override fun toString() = "PathDoesNotExist(${path.java}${ if (exception != null) ", $exception" else "" })"
}

data class IOError(
    override val path: Path,
    override val exception: IOException
) : FileError, DeleteError, Exceptional {
    override fun toString() = "IOError(${path.java}, $exception)"
}