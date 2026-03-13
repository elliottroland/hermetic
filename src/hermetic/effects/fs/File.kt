package hermetic.effects.fs

import hermetic.either.*
import java.io.*
import java.time.Instant
import java.nio.file.Paths
import java.io.File as JFile
import java.nio.file.Path as JPath

/**
 * Represents a possibly-non-existent file or directory. The [FileSystem] is responsible for
 * resolving these to instances of [FileOrDir], or surfacing the relevant errors when dealing
 * with the OS.
 * 
 * The "reference dir" of a path is determined by the file system which is resolving it, and it
 * need not be the current working directory.
 * 
 * This is a more restrictive version of the [java.nio.file.Path], but the latter can be
 * recovered using [java].
 */
data class Path(val java: JPath) {
    fun resolve(other: Path) = Path(java.resolve(other.java))
    fun resolve(other: String) = Path(java.resolve(other))
    fun resolve(other: JPath) = Path(java.resolve(other))

    val absolute get() = if (java.isAbsolute()) this else Path(java.toAbsolutePath())
    val isAbsolute get() = java.isAbsolute()
    val isRelative get() = !isAbsolute
    
    fun relativize(other: Path) = Path(absolute.java.relativize(other.absolute.java))
    fun relativeTo(root: Path) = root.relativize(this)

    fun startsWith(other: Path) = java.startsWith(other.java)
    fun startsWith(str: String) = java.startsWith(str)

    override fun toString() = "Path($java)"

    companion object {
        fun of(str: String) = Path(Paths.get(str))
    }
}

fun JPath.resolve(other: Path) =
    Path(this.resolve(other.java))

/**
 * Represents an existing [File] or [Dir] in the [FileSystem]. These cannot be constructed directly
 * but must be resolved using a [FileSystem].
 * 
 * An instance of this class always refers to a resolvable file, regardless of the file system which resolves
 * them.
 * 
 * This is a more restrictive version of the [java.io.File], but the latter can be recovered using [java].
 */
sealed interface FileOrDir {
    val java: JFile
    val path: Path
    val exists get() = java.exists()
    val name get() = java.name
    val readable get() = java.canRead()
    val writeable get() = java.canWrite()
    val lastModified get() = Instant.ofEpochMilli(java.lastModified())

    fun fileOrNull(): File? = this as? File
    fun dirOrNull(): Dir? = this as? Dir
}

class File internal constructor(override val java: JFile) : FileOrDir {
    override val path by lazy { Path(java.toPath()) }

    val extension get() = java.extension
    val executable get() = java.canExecute()
    val sizeBytes get() = java.length()

    context(fs: FileSystem)
    fun inputStream(): Either<FileError, InputStream> = fs.inputStream(this)

    context(fs: FileSystem)
    fun reader(): Either<FileError, Reader> = inputStream().map { it.reader() }
    
    context(fs: FileSystem)
    fun outputStream(): Either<FileError, OutputStream> = fs.outputStream(this)

    context(fs: FileSystem)
    fun writer(): Either<FileError, Writer> = outputStream().map { it.writer() }

    override fun toString() = "File($java)"
    override fun hashCode() = java.hashCode()
    override fun equals(other: Any?) = other is File && java == other.java
}

class Dir internal constructor(override val java: JFile) : FileOrDir {
    override val path by lazy { Path(java.toPath()) }

    context(fs: FileSystem)
    fun files() = fs.listFiles(this)

    context(fs: FileSystem)
    fun dirs() = fs.listDirs(this)

    fun resolve(path: Path): Path = this.path.resolve(path)
    fun resolve(path: String): Path = this.path.resolve(path)

    override fun toString() = "Dir($java)"
    override fun hashCode() = java.hashCode()
    override fun equals(other: Any?) = other is Dir && java == other.java
}