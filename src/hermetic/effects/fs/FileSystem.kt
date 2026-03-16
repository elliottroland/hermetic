package hermetic.effects.fs

import hermetic.either.*
import hermetic.effects.Async
import java.io.*
import java.nio.file.*
import java.nio.charset.*
import kotlin.io.*

sealed interface Lifespan {
    object Persistent: Lifespan
    class Ephemeral 
}
enum class Scope { Global, Restricted }

class FinalizationError(val errors: List<Any>) : hermetic.Err(errors) {
    override val message = "Failed to clean up all ephemeral resources"
}

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
interface FileSystem<Lifespan, Scope> : Finalizable<FinalizationError> {
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

fun FileSystem.get(path: String) = get(Path.of(path))
fun FileSystem.createFile(path: String, mkdirs: Boolean = false) = createFile(Path.of(path), mkdirs)
fun FileSystem.createFile(parentDir: Dir, filename: String, mkdirs: Boolean = false) = createFile(parentDir.resolve(filename), mkdirs)
fun FileSystem.createDir(path: String, mkdirs: Boolean = false) = createDir(Path.of(path), mkdirs)
fun FileSystem.createDir(parentDir: Dir, dirName: String, mkdirs: Boolean = false) = createDir(parentDir.resolve(dirName), mkdirs)
fun FileSystem.getFile(path: String) = getFile(Path.of(path))
fun FileSystem.getFileOrNull(path: String) = getFileOrNull(Path.of(path))
fun FileSystem.getOrCreateFile(path: String, mkdirs: Boolean = false) = getOrCreateFile(Path.of(path), mkdirs)
fun FileSystem.getDir(path: String) = getDir(Path.of(path))
fun FileSystem.getDirOrNull(path: String) = getDirOrNull(Path.of(path))
fun FileSystem.getOrCreateDir(path: String, mkdirs: Boolean = false) = getOrCreateDir(Path.of(path), mkdirs)
fun FileSystem.restrictFs(path: String, mkdirs: Boolean = false) = restrictFs(Path.of(path), mkdirs)

/**
 * A [FileSystem] which is not scoped to any particular root directory. Generally, this should
 * not be used, and rather a [RestrictedFileSystem] or [EphemeralFileSystem] should be used instead.
 * 
 * If this file system is created with an [async], then all [inputStream]s and [outputStream]s are
 * wrapped in asynchronous versions, offloading all disk IO to a dedicated async pool. This is the
 * recommended mechanism for disk IO, since it allows these operations to be scaled to the needs of
 * the underlying host independently of the workload of your application code.
 */
typealias GlobalFileSystem = FileSystem<Lifespan.Persistent, Scope.Global>

interface RestrictedFileSystem : FileSystem

/**
 * A [FileSystem] which will delete all files created with it when [finalize]d.
 */
interface EphemeralFileSystem : FileSystem, Finalizable<EphemeralFileSystem.FinalizationError> {
    
}

interface PersistentFileSystem : FileSystem