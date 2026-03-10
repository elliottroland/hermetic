package hermetic.effects.fs

import java.io.*
import java.nio.file.*
import java.nio.charset.*

// TODO: Should we return more detailed errors for create file? This currently seems to rely on exceptions.
//       Yes: we should model errors, and then have orNull() functions for everything.
// TODO: We should expose functions for listing and walking directories. Although these can also be thought of as actions on the file.

sealed interface CreateFileError
sealed interface CreateDirError
sealed interface GetError
sealed interface GetFileError
sealed interface GetDirError
sealed interface DeleteFileError
sealed interface DeleteDirError
sealed interface DeleteError

class ParentDirDoesNotExist(path: Path) : CreateFileError, CreateDirError, DeleteFileError, DeleteDirError, DeleteError
class PermissionDenied : CreateFileError, CreateDirError, DeleteFileError, DeleteDirError, DeleteError
class PathIsDir(path: Path) : GetFileError, CreateFileError, DeleteFileError, DeleteError
class PathIsFile(path: Path) : GetDirError, CreateDirError, DeleteDirError, DeleteError
class PathDoesNotExist(path: Path) : GetFileError, GetDirError, GetError, DeleteError

/**
 * An effect which exposes the underlying file system in different ways, depending on the
 * specific effect used. For an initial version, this class of effects only deals with fetching,
 * creating, and deleting files which may or may not exist. Reading and writing files is still
 * handled through the relevant file objects.
 * 
 * In general, [FileSystem]s treat [Path]s as references to files which may or may not exist, but
 * [File]s as references to files which are known to exist.
 * 
 * [FileSystem]s distinguish between [File]s and [Dir]s, since (1) conflating them is error prone
 * and (2) the structure of a folder is typically known ahead of time. There is a more general
 * [FileOrDir] type, but it is not expected that you will need this very often.
 */
interface FileSystem {
    fun get(path: Path): FileOrDir?

    /**
     * Returns the file at the given [path], if it exists.
     */
    fun getFile(path: Path): File? = get(path).fileOrNull()

    /**
     * Attempts to create the file at the given [path], and returns the resulting file if
     * successful. If a file already existed at the given path, then null is returned.
     */
    fun createFile(path: Path, mkdirs: Boolean): File?

    fun getOrCreateFile(path: Path): File = getFile(path) ?: createFile(path, mkdirs = true)!!

    /**
     * Creates a file in the existent [dir], starting with the [prefix] and ending with the [suffix].
     * The prefix must be at least three characters long.
     */
    fun createTempFile(dir: Dir, prefix: String, suffix: String): File

    fun delete(fileOrDir: FileOrDir): Boolean

    /**
     * Attempts to delete the file at the given [path], returning whether there was any
     * file to delete.
     */
    fun deleteFile(path: Path): Boolean = getFile(path)?.let { delete(it) } ?: false
    fun deleteDir(path: Path): Boolean = getDir(path)?.let { delete(it) } ?: false

    fun getDir(path: Path): Dir? = get(path).dirOrNull()
    fun createDir(path: Path, mkdirs: Boolean): Dir?
    fun getOrCreateDir(path: Path): Dir = getDir(path) ?: createDir(path, mkdirs = true)!!
    
    fun list(dir: Dir): Sequence<FileOrDir>
    fun listFiles(dir: Dir): Sequence<File> = list(dir).filterIsInstance<File>()
    fun listDirs(dir: Dir): Sequence<Dir> = list(dir).filterIsInstance<Dir>()

    fun inputStream(file: File): InputStream
    fun outputStream(file: File): OutputStream

    /**
     * Creates a new [RestrictedFileSystem] at the given path. The directories leading to
     * the root are automatically created.
     */
    // fun restrictedFileSystem(root: Path): RestrictedFileSystem

    /**
     * Createa a new [EphemeralFileSystem] at the given path. 
     */
    // fun ephemeralFileSystem(root: Path): EphemeralFileSystem
}

/**
 * A [FileSystem] which is not scoped to any particular root directory. Generally, this should
 * not be used, and rather a [RestrictedFileSystem] or [EphemeralFileSystem] should be used instead.
 */
interface GlobalFileSystem : FileSystem

/**
 * A [FileSystem] which is scoped to a particular [root] directory. All paths are interpreted
 * relative to that. Any attempts to reach out of this will result in an error being thrown.
 */
interface RestrictedFileSystem : FileSystem {
    fun fsRoot(): Path
}

/**
 * A [FileSystem] which is similar to a [RestrictedFileSystem], but which deletes all the files
 * created when its closed.
 */
interface EphemeralFileSystem : FileSystem, Closeable {
    fun fsRoot(): Path
}

class GlobalFileSystemDefault : GlobalFileSystem {
    override fun getFile(path: Path): File? = path.toFile().takeIf { it.exists() && it.isFile() }?.let { File(it) }
    override fun createFile(path: Path, mkdirs: Boolean): File? = path.toFile().also { if (mkdirs) it.parent().mkdirs() }.takeIf { it.createNewFile() }?.let { File(it) }
    override fun createTempFile(dir: Dir, prefix: String, suffix: String): File = java.io.File.createTempFile(prefix, suffix, dir.file).let { File(it) }
    override fun deleteFile(path: Path): Boolean = path.toFile().delete()
    override fun getDir(path: Path): Dir? = path.toFile().takeIf { it.exists() && it.isDirectory() }?.let { Dir(it) }
    override fun createDir(path: Path, mkdirs: Boolean): Dir? = path.toFile().takeIf { it.mkdirs() }?.let { Dir(it) }
    override fun list(dir: Dir): Sequence<FileOrDir>
    override fun inputStream(file: File): InputStream = file.file.inputStream()
    override fun outputStream(file: File): OutputStream = file.file.outputStream()
}

/**
 * The standard implementation for file access, which 
 */
class RestrictedFileSystemDefault(private val root: Path) : RestrictedFileSystem {
    override fun fsRoot() = root
    override fun getFile(path: Path): File? = root.resolve(path).toFile().takeIf { it.exists() }?.let { File(it) }
    override fun createFile(path: Path, mkdirs: Boolean): File? = root.resolve(path).toFile().takeIf { it.createNewFile() }?.let { File(it) }
    override fun deleteFile(path: Path): Boolean = root.resolve(path).toFile().delete()
    override fun createDir(path: Path, mkdirs: Boolean) = root.resolve(path).toFile().takeIf { if (mkdirs) it.mkdirs() else it.mkdir() }?.let { Dir(it) }
}

class EphemeralFileSystemDefault(root: Path) : EphemeralFileSystem {
    private val rfs = RestrictedFileSystemDefault(root)
    private val filesToClean = mutableListOf<java.io.File>()

    override fun fsRoot() =
        rfs.fsRoot()

    override fun getFile(path: Path): File? =
        rfs.getFile(path)

    @Synchronized override fun createFile(path: Path, mkdirs: Boolean): File? =
        rfs.createFile(path, mkdirs)?.also { it.file.deleteOnExit(); filesToClean.add(0, it.file)}

    override fun deleteFile(path: Path): Boolean =
        rfs.deleteFile(path)

    @Synchronized override fun createDir(path: Path, mkdirs: Boolean) =
        rfs.createDir(path, mkdirs)?.also { it.file.deleteOnExit(); filesToClean.add(0, it.file) }
        
    @Synchronized override fun close() {
        val exceptions = mutableListOf<Exception>()
        for (file in filesToClean) {
            try {
                file.delete()
            } catch (e: Exception) {
                exceptions.add(e)
            }
        }
    }
}