package hermetic.effects.fs

import hermetic.either.*
import hermetic.effects.Async
import java.io.*
import java.nio.file.*
import java.nio.charset.*
import kotlin.io.*

/**
 * A [FileSystem] which is scoped to a particular [rootDir] directory. All paths are interpreted
 * relative to that. Any attempts to reach out of this will result in an error being thrown.
 * 
 * If an absolute [Path] is given which is within the [rootDir], it will be rewritten to match
 * the [rootDir]. If you pass a [FileOrDir] into a function here which was computed from another
 * file system, it will throw an exception if it looks like it is not relative to this root. Otherwise,
 * it will be assumed to have been computed from this file system. This can lead to unexpected
 * behavior, and is not recommended. If you don't know whether a particular file or dir is part
 * of this file system, then it is better to [import] it first.
 */
interface RestrictedFileSystem : FileSystem {
    fun rootDir(): Dir
    fun ephemeral(): EphemeralFileSystem
}

open class DefaultRestrictedFileSystem(private val rootDir: Dir, private val gfs: GlobalFileSystem) : RestrictedFileSystem {
    override fun rootDir(): Dir = rootDir
    
    override fun get(path: Path) =
        gfs.get(resolve(path))
    
    override fun createFile(path: Path, mkdirs: Boolean) =
        gfs.createFile(resolve(path), mkdirs)

    override fun createTempFile(dir: Dir, prefix: String, suffix: String) =
        gfs.createTempFile(resolve(dir), prefix, suffix)

    override fun createDir(path: Path, mkdirs: Boolean) =
        gfs.createDir(resolve(path), mkdirs)

    override fun delete(ford: FileOrDir) =
        gfs.delete(resolve(ford))

    override fun inputStream(file: File) =
        gfs.inputStream(resolve(file))

    override fun outputStream(file: File) =
        gfs.outputStream(resolve(file))

    override fun walk(dir: Dir, maxDepth: Int, direction: FileWalkDirection, shouldEnter: (Dir) -> Boolean) =
        gfs.walk(resolve(dir), maxDepth, direction, shouldEnter)

    override fun restrictFs(rootDir: Dir) =
        gfs.restrictFs(resolve(rootDir))

    override fun ephemeral() =
        DefaultEphemeralFileSystem(this)

    /**
     * Converts the [path] to something starting with the [rootDir]'s path.
     */
    internal fun resolve(path: Path): Path =
        when {
            path.startsWith(rootDir.path) -> return path
            path.isAbsolute -> rootDir.path.relativize(path)
            else -> path
        }.also {
            require(!it.startsWith("..")) { "Expected path ${path.absolute} to be below root ${rootDir.path.absolute}" }
        }.let {
            rootDir.path.resolve(it)
        }
    
    @Suppress("UNCHECKED_CAST")
    internal fun <T : FileOrDir> resolve(ford: T): T =
        if (ford.path.java.startsWith(rootDir.path.java)) {
            ford
        } else {
            val path = resolve(ford.path.absolute)
            when (ford) {
                is File -> File(path.java.toFile())
                is Dir -> Dir(path.java.toFile())
            } as T
        }
}