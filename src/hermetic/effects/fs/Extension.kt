package hermetic.effects.fs

fun FileSystem<*, *>.get(path: String) = get(Path.of(path))
fun FileSystem<*, *>.createFile(path: String, mkdirs: Boolean = false) = createFile(Path.of(path), mkdirs)
fun FileSystem<*, *>.createFile(parentDir: Dir, filename: String, mkdirs: Boolean = false) = createFile(parentDir.resolve(filename), mkdirs)
fun FileSystem<*, *>.createDir(path: String, mkdirs: Boolean = false) = createDir(Path.of(path), mkdirs)
fun FileSystem<*, *>.createDir(parentDir: Dir, dirName: String, mkdirs: Boolean = false) = createDir(parentDir.resolve(dirName), mkdirs)
fun FileSystem<*, *>.getFile(path: String) = getFile(Path.of(path))
fun FileSystem<*, *>.getFileOrNull(path: String) = getFileOrNull(Path.of(path))
fun FileSystem<*, *>.getOrCreateFile(path: String, mkdirs: Boolean = false) = getOrCreateFile(Path.of(path), mkdirs)
fun FileSystem<*, *>.getDir(path: String) = getDir(Path.of(path))
fun FileSystem<*, *>.getDirOrNull(path: String) = getDirOrNull(Path.of(path))
fun FileSystem<*, *>.getOrCreateDir(path: String, mkdirs: Boolean = false) = getOrCreateDir(Path.of(path), mkdirs)
fun FileSystem<*, *>.restricted(path: String, mkdirs: Boolean = false) = restricted(Path.of(path), mkdirs)

fun FileSystem<*, Scope.Restricted>.rootDir(): Dir = scope.rootDir
