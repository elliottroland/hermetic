package hermetic.effects.fs

import hermetic.either.*
import hermetic.effects.Async
import java.io.*
import java.nio.file.*
import java.nio.charset.*
import kotlin.io.*

fun main() {
    // val gfs = GlobalFileSystem(null)
    // val root = gfs.getDir(Path.of("src")).getOrThrow()
    // context(RestrictedFileSystem(root, gfs)) {
    val fs = GlobalFileSystem(null).restrictFs(Path.of("src")).getOrThrow()
    context(fs) {
        doSomething()
    }
}

context(fs: RestrictedFileSystem)
fun doSomething() {
    // println(fs.get(Path.of("hello.world")))
    // println(fs.get(Path.of("src")))
    // println(fs.getFile(Path.of("src")))
    // println(fs.getFileOrNull(Path.of("src")))
    // println(fs.get(Path.of("README.md")))
    // println(fs.getOrCreateDir(Path.of("README.md"), mkdirs = false))
    // println(fs.getOrCreateDir(Path.of("README.md"), mkdirs = false).recoverToNullIf { it is PathIsFile })
    // println(fs.createFile(Path.of("README.md"), mkdirs = false).recover { if (it is PathIsFile) ok(it.file) else err(it) })
    // println(fs.getOrCreateFile(Path.of("README.md"), mkdirs = false))
    // println(fs.inputStream(File(Paths.get("hello.world").toFile())))
    val dir = fs.getDir(Path.of("hermetic")).getOrThrow()
    // val walk = fs.walk(dir)
    for (ford in fs.list(dir)) {
        println(ford)
    }

    // println(fs.resolve(File(java.io.File("README.md"))))

    // println(fs.getDir(Path.of("./something")))
    // fs.getDir(Path.of("/Users/elliottroland/Desktop/hermetic"))
}

// TODO: We should expose functions for listing and walking directories. Although these can also be thought of as actions on the file.

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
    fun createFile(path: Path, mkdirs: Boolean = false): Either<CreateError, File>
    /**
     * Creates a file in the existent [dir], starting with the [prefix] and ending with the [suffix].
     * The prefix must be at least three characters long.
     */
    fun createTempFile(dir: Dir, prefix: String, suffix: String = ".tmp"): Either<CreateError, File>

    fun createDir(path: Path, mkdirs: Boolean = false): Either<CreateError, Dir>

    /**
     * Attempts to delete the file at the given [path], returning whether there was anything
     * to delete.
     */
    fun delete(ford: FileOrDir): Either<DeleteError, Boolean>

    /**
     * Begins a walk up or down the file tree rooted at the [dir].
     */
    fun walk(
        dir: Dir,
        maxDepth: Int = Int.MAX_VALUE,
        direction: FileWalkDirection = FileWalkDirection.TOP_DOWN,
        shouldEnter: (Dir) -> Boolean = { true }
    ): Sequence<FileOrDir>

    fun inputStream(file: File): Either<FileError, InputStream>
    fun outputStream(file: File): Either<FileError, OutputStream>

    /**
     * Creates a new [RestrictedFileSystem] rooted at the given [rootDir].
     */
    fun restrictFs(rootDir: Dir): RestrictedFileSystem

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
    fun getOrCreateFile(path: Path, mkdirs: Boolean = false): Either<CreateError, File> =
        createFile(path, mkdirs).recover { if (it is PathIsFile) ok(it.file) else err(it) }

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
    fun getOrCreateDir(path: Path, mkdirs: Boolean = false): Either<CreateError, Dir> =
        createDir(path, mkdirs).recover { if (it is PathIsDir) ok(it.dir) else err(it) }
    
    fun list(dir: Dir): Sequence<FileOrDir> =
        walk(dir, maxDepth = 1, direction = FileWalkDirection.TOP_DOWN, shouldEnter = { true }).drop(1)

    fun listFiles(dir: Dir): Sequence<File> =
        list(dir).filterIsInstance<File>()

    fun listDirs(dir: Dir): Sequence<Dir> =
        list(dir).filterIsInstance<Dir>()
    
    fun restrictFs(path: Path, mkdirs: Boolean = false): Either<CreateError, RestrictedFileSystem> =
        getOrCreateDir(path, mkdirs).map { restrictFs(it) }
}

/**
 * A [FileSystem] which is similar to a [RestrictedFileSystem], but which deletes all the files
 * created when its closed.
 */
// interface EphemeralFileSystem : FileSystem, Closeable {
//     fun fsRoot(): Path
// }

/**
 * A [FileSystem] which is scoped to a particular [root] directory. All paths are interpreted
 * relative to that. Any attempts to reach out of this will result in an error being thrown.
 */
// interface RestrictedFileSystem : FileSystem {
//     fun fsRoot(): Path
// }

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