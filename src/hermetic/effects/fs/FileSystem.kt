package hermetic.effects.fs

import hermetic.Err
import hermetic.Finalizable
import hermetic.clearStackTrace
import hermetic.effects.Async
import hermetic.effects.fs.FileSystem.Companion.ephemeralGlobal
import hermetic.effects.fs.FileSystem.Companion.ephemeralRestricted
import hermetic.effects.fs.FileSystem.Companion.global
import hermetic.effects.fs.FileSystem.Companion.restricted
import hermetic.either.Either
import hermetic.either.err
import hermetic.either.flatMap
import hermetic.either.getOrNull
import hermetic.either.getOrThrow
import hermetic.either.map
import hermetic.either.mapErr
import hermetic.either.ok
import hermetic.either.recover
import hermetic.either.recoverIf
import hermetic.either.recoverToNullIf
import java.io.InputStream
import java.io.OutputStream

/**
 * An effect which exposes the underlying file system in a type-safe way. The exact behavior of a [FileSystem] is governed by the
 * [Lifespan] and [Scope] by which it is parameterized:
 *
 * - The [Lifespan] governs whether [File]s and [Dir]s created with this file system are automatically cleaned up when it is [finalize]d.
 *   [Persistent][Lifespan.Persistent] file systems don't clean up anything, while [ephemeral][Lifespan.Ephemeral] file systems do.
 * - The [Scope] governs whether [Path]s are interpreted relative to a specific directory or not. [Global][Scope.Global] file systems interpret
 *   relative paths relative to the current working directory and can reference any file on the system. By contrast, [restricted][Scope.Restricted]
 *   file systems interpret relative paths relative to their [rootDir][Scope.Restricted.rootDir] and do not permit the access of files outside
 *   this root directory.
 *
 * In general, [FileSystem]s treat [Path]s as references to files which may or may not exist, but [File]s, [Dir]s, and [FileOrDir]s as references
 * to files which are known to exist. [FileSystem]s distinguish between [File]s and [Dir]s, since (1) conflating them is error-prone and (2) the
 * structure of a folder is typically known ahead of time. There is a more general [FileOrDir] type, but it is not expected that you will need
 * this very often.
 *
 * For constructing a [FileSystem] instance, it is recommended that you use one of the builders provided here: [global], [restricted], [ephemeralGlobal],
 * and [ephemeralRestricted].
 *
 * For declaring effects on file systems, a number of type aliases are defined to make this easier: [GlobalFileSystem], [RestrictedFileSystem],
 * [EphemeralGlobalFileSystem], and [EphemeralRestrictedFileSystem]. It is generally recommended that code know what kind of file system is it
 * contributing to, so that it knows whether it needs to manage clean up or how to refer to files. If you want more flexibility, then you can achieve
 * this by explicitly specifying [FileSystem] type, for example:
 * ```
 * context(fs: FileSystem<*, Scope.Restricted>)
 * context(fs: FileSystem<Lifespan.Persistent, *>)
 * ```
 */
interface FileSystem<out L : Lifespan, out S : Scope> : Finalizable<FinalizationError> {
    companion object {
        /**
         * Returns a new [persistent][Lifespan.Persistent] [global][Scope.Global] file system. This most closely resembles "normal"
         * file system access. If [async] is true, the resulting file system's [inputStream]s and [outputStream]s are offloaded to
         * the dedicated [Async.IO] for transparent bounded IO access.
         */
        fun global(async: Boolean = false): GlobalFileSystem =
            DefaultFileSystem(Lifespan.Persistent, Scope.Global, if (async) Async.IO else null)

        /**
         * Returns a new [persistent][Lifespan.Persistent] file system which is [restricted][Scope.Restricted] to the given [rootDir].
         * If [async] is true, the resulting file system's [inputStream]s and [outputStream]s are offloaded to the dedicated [Async.IO] for
         * transparent bounded IO access.
         */
        fun restricted(rootDir: Dir, async: Boolean = false): RestrictedFileSystem =
            global(async).restricted(rootDir)

        fun restricted(rootDir: Path, async: Boolean = false): RestrictedFileSystem =
            global(async).let { it.restricted(it.getOrCreateDir(rootDir).getOrThrow()) }

        fun restricted(rootDir: String, async: Boolean = false): RestrictedFileSystem =
            global(async).let { it.restricted(it.getOrCreateDir(rootDir).getOrThrow()) }

        /**
         * Returns a new [ephemeral][Lifespan.Ephemeral] file system which is also [global][Scope.Global].
         * If [async] is true, the resulting file system's [inputStream]s and [outputStream]s are offloaded to the dedicated [Async.IO] for
         * transparent bounded IO access.
         */
        fun ephemeralGlobal(async: Boolean = false): EphemeralGlobalFileSystem =
            global(async).ephemeral()

        /**
         * Returns a new [ephemeral][Lifespan.Ephemeral] file system which is [restricted][Scope.Restricted] to the given [rootDir].
         * If [async] is true, the resulting file system's [inputStream]s and [outputStream]s are offloaded to the dedicated [Async.IO] for
         * transparent bounded IO access.
         */
        fun ephemeralRestricted(rootDir: Dir, async: Boolean = false): EphemeralRestrictedFileSystem =
            restricted(rootDir, async).ephemeral()
    }

    /**
     * The [Lifespan] which governs whether [File]s and [Dir]s created by this file system will be deleted when it is [finalize]d.
     */
    val lifespan: L

    /**
     * The [Scope] which governs how relative [Path]s are interpreted by this file system, and whether access to paths is restricted
     * to a particular [rootDir][Scope.Restricted.rootDir] or not.
     */
    val scope: S

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
    fun delete(ford: FileOrDir): Either<DeleteError, Unit>

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
     * Creates a new [restricted][Scope.Restricted] file system rooted at the given [rootDir].
     */
    fun restricted(rootDir: Dir): FileSystem<L, Scope.Restricted>

    /**
     * Creates a new [ephemeral][Lifespan.Ephemeral] file system from this file system.
     */
    fun ephemeral(): FileSystem<Lifespan.Ephemeral, S>

    // -- Default implementations --

    /**
     * If the current file system is [ephemeral][Lifespan.Ephemeral], then all [File]s and [Dir]s created with it will be deleted. If
     * this file system is [persistent][Lifespan.Persistent], then this does nothing.
     *
     * If any errors are encountered while cleaning up resources, then they are returned as part of the [FinalizationError]. If no errors
     * are occurred, then null is returned instead.
     */
    override fun finalize(): FinalizationError? {
        val toDelete = (lifespan as? Lifespan.Ephemeral)
            ?.registered()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val errors = mutableListOf<Any>()
        for (ford in toDelete) {
            delete(ford).onErr { errors.add(it.also { if (it is Err) it.clearStackTrace() }) }
        }
        if (errors.isEmpty()) {
            return null
        }
        return FinalizationError(errors)
    }

    /**
     * Resolves the [path] to a [File] if it exists, otherwise returns an error.
     */
    fun getFile(path: Path): Either<GetFileError, File> =
        get(path).mapErr {
            when (it) {
                is PathDoesNotExist -> it
            }
        }.flatMap { ford ->
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
    fun getDir(path: Path): Either<GetDirError, Dir> =
        get(path).mapErr {
            when (it) {
                is PathDoesNotExist -> it
            }
        }.flatMap { ford ->
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

    fun deleteIfExists(ford: FileOrDir): Either<DeleteError, Boolean> =
        delete(ford).map { true }.recoverIf({ it is PathDoesNotExist }, { false })
    
    fun list(dir: Dir): Sequence<FileOrDir> =
        walk(dir, maxDepth = 1, direction = FileWalkDirection.TOP_DOWN, shouldEnter = { true }).drop(1)

    fun listFiles(dir: Dir): Sequence<File> =
        list(dir).filterIsInstance<File>()

    fun listDirs(dir: Dir): Sequence<Dir> =
        list(dir).filterIsInstance<Dir>()
    
    fun restricted(path: Path, mkdirs: Boolean = false): Either<CreateError, FileSystem<L, Scope.Restricted>> =
        getOrCreateDir(path, mkdirs).map { restricted(it) }
}
