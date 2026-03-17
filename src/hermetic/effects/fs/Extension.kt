package hermetic.effects.fs

import hermetic.either.Either
import hermetic.either.map

// -- Get --
fun FileSystem<*, *>.get(path: String) = get(Path.of(path))
fun FileSystem<*, *>.getFile(path: String) = getFile(Path.of(path))
fun FileSystem<*, *>.getFileOrNull(path: String) = getFileOrNull(Path.of(path))
fun FileSystem<*, *>.getDir(path: String) = getDir(Path.of(path))
fun FileSystem<*, *>.getDirOrNull(path: String) = getDirOrNull(Path.of(path))

// -- Create --
fun FileSystem<*, *>.createFile(path: String, mkdirs: Boolean = false) = createFile(Path.of(path), mkdirs)
fun FileSystem<*, *>.createFile(parentDir: Dir, filename: String, mkdirs: Boolean = false) = createFile(parentDir.resolve(filename), mkdirs)
fun FileSystem<*, *>.createDir(path: String, mkdirs: Boolean = false) = createDir(Path.of(path), mkdirs)
fun FileSystem<*, *>.createDir(parentDir: Dir, dirName: String, mkdirs: Boolean = false) = createDir(parentDir.resolve(dirName), mkdirs)

// -- Get or create --
fun FileSystem<*, *>.getOrCreateFile(path: String, mkdirs: Boolean = false) = getOrCreateFile(Path.of(path), mkdirs)
fun FileSystem<*, *>.getOrCreateDir(path: String, mkdirs: Boolean = false) = getOrCreateDir(Path.of(path), mkdirs)

// -- Restrictions --
fun <L : Lifespan> FileSystem<L, *>.restricted(rootPath: Path): Either<CreateError, FileSystem<L, Scope.Restricted>> =
    getOrCreateDir(rootPath).map { restricted(it) }
fun <L : Lifespan> FileSystem<L, *>.restricted(rootPath: String): Either<CreateError, FileSystem<L, Scope.Restricted>> =
    getOrCreateDir(rootPath).map { restricted(it) }

fun FileSystem.Companion.restricted(rootDir: Path, async: Boolean = false): Either<CreateError, RestrictedFileSystem> =
    global(async).let { gfs -> gfs.getOrCreateDir(rootDir).map { gfs.restricted(it) } }
fun FileSystem.Companion.restricted(rootDir: String, async: Boolean = false): Either<CreateError, RestrictedFileSystem> =
    global(async).let { gfs -> gfs.getOrCreateDir(rootDir).map { gfs.restricted(it) } }

// -- Special cases --
fun FileSystem<*, Scope.Restricted>.rootDir(): Dir = scope.rootDir
