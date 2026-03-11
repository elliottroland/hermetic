package hermetic.effects.fs

import hermetic.either.*
import hermetic.effects.Async
import java.io.*
import java.nio.file.*
import java.nio.charset.*

fun main() {
    context(GlobalFileSystem(null)) {
        doSomething()
    }
}

context(fs: FileSystem)
fun doSomething() {
    println(fs.get(Paths.get("hello.world")))
    println(fs.get(Paths.get("src")))
    println(fs.getFile(Paths.get("src")))
    println(fs.getFileOrNull(Paths.get("src")))
    println(fs.get(Paths.get("README.md")))
    println(fs.getOrCreateDir(Paths.get("README.md"), mkdirs = false))
    println(fs.getOrCreateDir(Paths.get("README.md"), mkdirs = false).recoverToNullIf { it is PathIsFile })
    println(fs.createFile(Paths.get("README.md"), mkdirs = false).tryRecover { if (it is PathIsFile) ok(it.file) else err(it) })
    println(fs.getOrCreateFile(Paths.get("README.md"), mkdirs = false))
}

// TODO: Should we return more detailed errors for create file? This currently seems to rely on exceptions.
//       Yes: we should model errors, and then have orNull() functions for everything.
// TODO: We should expose functions for listing and walking directories. Although these can also be thought of as actions on the file.

sealed interface GetError
sealed interface CreateError
sealed interface DeleteError
sealed interface FileError

data class ParentPathDoesNotExist(val path: Path) : CreateError, DeleteError
data class PermissionDenied(val path: Path, override val exception: SecurityException) : GetError, CreateError, DeleteError, FileError, Exceptional
data class PathIsDir(val dir: Dir) : GetError, CreateError
data class PathIsFile(val file: File) : GetError, CreateError
data class PathDoesNotExist(val path: Path, override val exception: FileNotFoundException?) : GetError, DeleteError, FileError, Exceptional
data class IOError(val path: Path, override val exception: IOException) : FileError, DeleteError, Exceptional

/**
 * An effect which exposes the underlying file system in different ways, depending on the
 * specific effect used. For an initial version, this class of effects only deals with fetching,
 * creating, and deleting files which may or may not exist. Reading and writing files is still
 * handled through the relevant file objects.
 * 
 * In general, [FileSystem]s treat [Path]s as references to files which may or may not exist, but
 * [File]s, [Dirs]s, and [FileOrDir]s as references to files which are known to exist.
 * 
 * [FileSystem]s distinguish between [File]s and [Dir]s, since (1) conflating them is error prone
 * and (2) the structure of a folder is typically known ahead of time. There is a more general
 * [FileOrDir] type, but it is not expected that you will need this very often.
 */
interface FileSystem {
    fun get(path: Path): Either<GetError, FileOrDir>

    /**
     * Attempts to create the file at the given [path], and returns the resulting file if
     * successful. If a file already existed at the given path, then null is returned.
     */
    fun createFile(path: Path, mkdirs: Boolean): Either<CreateError, File>
    /**
     * Creates a file in the existent [dir], starting with the [prefix] and ending with the [suffix].
     * The prefix must be at least three characters long.
     */
    fun createTempFile(dir: Dir, prefix: String, suffix: String): Either<CreateError, File>

    fun createDir(path: Path, mkdirs: Boolean): Either<CreateError, Dir>

    /**
     * Attempts to delete the file at the given [path], returning whether there was anything
     * to delete.
     */
    fun delete(ford: FileOrDir): Either<DeleteError, Boolean>
    
    // TODO: Walk should be primary, and list an extension
    fun list(dir: Dir): Sequence<FileOrDir>

    fun inputStream(file: File): Either<FileError, InputStream>
    fun outputStream(file: File): Either<FileError, OutputStream>

    /**
     * Creates a new [RestrictedFileSystem] at the given path. The directories leading to
     * the root are automatically created.
     */
    // fun restrictedFileSystem(root: Path): RestrictedFileSystem

    /**
     * Createa a new [EphemeralFileSystem] at the given path. 
     */
    // fun ephemeralFileSystem(root: Path): EphemeralFileSystem


    // -- Default implementations --

    /**
     * Resolves the [path] to a [File] if it exists, otherwise returns an error.
     */
    fun getFile(path: Path): Either<GetError, File> =
        get(path).flatMap { ford ->
            when (ford) {
                is File -> ok(ford)
                is Dir -> err(PathIsDir(ford))
            }
        }
    
    /**
     * Resolves the [path] to a [File] if it exists, otherwise returns null.
     */
    fun getFileOrNull(path: Path): File? =
        getFile(path).getOrNull()

    /**
     * First tries to resolve the [path] to a [File], and if this fails because the file does not exist
     * then attempts to create it.
     */
    fun getOrCreateFile(path: Path, mkdirs: Boolean): Either<CreateError, File> =
        createFile(path, mkdirs).tryRecover { if (it is PathIsFile) ok(it.file) else err(it) }

    /**
     * Resolves the [path] to a [Dir] if it exists, otherwise returns an error.
     */
    fun getDir(path: Path): Either<GetError, Dir> =
        get(path).flatMap { ford ->
            when (ford) {
                is Dir -> ok(ford)
                is File -> err(PathIsFile(ford))
            }
        }
    
    /**
     * Resolves the [path] to a [Dir] if it exists, otherwise returns null.
     */
    fun getDirOrNull(path: Path): Dir? =
        getDir(path).getOrNull()

    /**
     * First tries to resolve the [path] to a [Dir], and if this fails because the directory does not exist
     * then attempts to create it.
     */
    fun getOrCreateDir(path: Path, mkdirs: Boolean): Either<CreateError, Dir> =
        createDir(path, mkdirs).tryRecover { if (it is PathIsDir) ok(it.dir) else err(it) }

    fun listFiles(dir: Dir): Sequence<File> =
        list(dir).filterIsInstance<File>()

    fun listDirs(dir: Dir): Sequence<Dir> =
        list(dir).filterIsInstance<Dir>()
}


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

/**
 * A [FileSystem] which is not scoped to any particular root directory. Generally, this should
 * not be used, and rather a [RestrictedFileSystem] or [EphemeralFileSystem] should be used instead.
 * 
 * If this file system is created with an [async], then all [inputStream]s and [outputStream]s are
 * wrapped in asynchronous versions, offloading all disk IO to a dedicated async pool. This is the
 * recommended mechanism for disk IO, since it allows these operations to be scaled to the needs of
 * the underlying host independently of the workload of your application code.
 */
class GlobalFileSystem(private val async: Async?) : FileSystem {
    override fun get(path: Path): Either<GetError, FileOrDir> {
        val file = path.toFile().getAbsoluteFile()
        return when {
            !file.exists() -> err(PathDoesNotExist(path, null))
            // TODO: PermissionDenied?
            else -> ok(file.toFileOrDir())
        }
    }

    override fun createFile(path: Path, mkdirs: Boolean): Either<CreateError, File> =
        try {
            val file = path.toFile().getAbsoluteFile()
            if (mkdirs) {
                file.parentFile.mkdirs()
            }
            when {
                file.createNewFile() -> ok(File(file))
                file.isDirectory() -> err(PathIsDir(Dir(file)))
                else -> err(PathIsFile(File(file)))
            }
        } catch (e: SecurityException) {
            err(PermissionDenied(path, e))
        } catch (e: IOException) {
            // TODO: Is this the right interpretation?
            err(ParentPathDoesNotExist(path))
        }

    override fun createTempFile(dir: Dir, prefix: String, suffix: String): Either<CreateError, File> =
        try {
            ok(File(java.io.File.createTempFile(prefix, suffix, dir.file.getAbsoluteFile())))
        } catch (e: SecurityException) {
            err(PermissionDenied(dir.path, e))
        } catch (e: IOException) {
            // TODO: Is this the right interpretation?
            err(ParentPathDoesNotExist(dir.path))
        }

    override fun createDir(path: Path, mkdirs: Boolean): Either<CreateError, Dir> =
        try {
            val file = path.toFile().getAbsoluteFile()
            if (mkdirs) {
                file.parentFile.mkdirs()
            }
            when {
                file.mkdir() -> ok(Dir(file))
                file.isFile() -> err(PathIsFile(File(file)))
                else -> err(PathIsDir(Dir(file)))
            }
        } catch (e: SecurityException) {
            err(PermissionDenied(path, e))
        } catch (e: IOException) {
            // TODO: Is this the right interpretation?
            err(ParentPathDoesNotExist(path))
        }

    override fun delete(ford: FileOrDir): Either<DeleteError, Boolean> =
        try {
            ok(ford.file.delete())
        } catch (e: SecurityException) {
            err(PermissionDenied(ford.path, e))
        } catch (e: IOException) {
            err(IOError(ford.path, e))
        }

    override fun list(dir: Dir): Sequence<FileOrDir> = TODO()

    override fun inputStream(file: File): Either<FileError, InputStream> =
        try {
            val inputStream = FileInputStream(file.file)
            when {
                async != null -> ok(AsyncInputStream(inputStream, async))
                else -> ok(inputStream)
            }
        } catch (e: SecurityException) {
            err(PermissionDenied(file.path, e))
        } catch (e: FileNotFoundException) {
            err(PathDoesNotExist(file.path, e))
        }

    override fun outputStream(file: File): Either<FileError, OutputStream> =
        try {
            val outputStream = FileOutputStream(file.file)
            when {
                async != null -> ok(AsyncOutputStream(outputStream, async))
                else -> ok(outputStream)
            }
        } catch (e: SecurityException) {
            err(PermissionDenied(file.path, e))
        } catch (e: FileNotFoundException) {
            err(PathDoesNotExist(file.path, e))
        }

    private fun java.io.File.toFileOrDir(): FileOrDir =
        when {
            isFile() -> File(this)
            else -> Dir(this)
        }
}

/**
 * The standard implementation for file access, which 
 */
// class RestrictedFileSystemDefault(private val root: Path) : RestrictedFileSystem {
//     override fun fsRoot() = root
//     override fun getFile(path: Path): File? = root.resolve(path).toFile().takeIf { it.exists() }?.let { File(it) }
//     override fun createFile(path: Path, mkdirs: Boolean): File? = root.resolve(path).toFile().takeIf { it.createNewFile() }?.let { File(it) }
//     override fun deleteFile(path: Path): Boolean = root.resolve(path).toFile().delete()
//     override fun createDir(path: Path, mkdirs: Boolean) = root.resolve(path).toFile().takeIf { if (mkdirs) it.mkdirs() else it.mkdir() }?.let { Dir(it) }
// }

// class EphemeralFileSystemDefault(root: Path) : EphemeralFileSystem {
//     private val rfs = RestrictedFileSystemDefault(root)
//     private val filesToClean = mutableListOf<java.io.File>()

//     override fun fsRoot() =
//         rfs.fsRoot()

//     override fun getFile(path: Path): File? =
//         rfs.getFile(path)

//     @Synchronized override fun createFile(path: Path, mkdirs: Boolean): File? =
//         rfs.createFile(path, mkdirs)?.also { it.file.deleteOnExit(); filesToClean.add(0, it.file)}

//     override fun deleteFile(path: Path): Boolean =
//         rfs.deleteFile(path)

//     @Synchronized override fun createDir(path: Path, mkdirs: Boolean) =
//         rfs.createDir(path, mkdirs)?.also { it.file.deleteOnExit(); filesToClean.add(0, it.file) }
        
//     @Synchronized override fun close() {
//         val exceptions = mutableListOf<Exception>()
//         for (file in filesToClean) {
//             try {
//                 file.delete()
//             } catch (e: Exception) {
//                 exceptions.add(e)
//             }
//         }
//     }
// }