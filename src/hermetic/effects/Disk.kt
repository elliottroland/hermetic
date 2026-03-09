package hermetic.effects

import java.io.*
import java.nio.file.*
import java.nio.charset.*

interface ReadDisk {
    /**
     * Returns the root of this effect, relative to which every file
     * will be returned when calling [file].
     */
    fun root(): Path?

    /**
     * Returns a file from the given [path]. If the path is relative,
     * then it is interpreted as relative to the [root].
     */
    fun fileIfExists(path: Path): File?

    /**
     * Returns the an input stream for the given file if it exists,
     * otherwise returns null.
     */
    fun inputStreamIfExists(file: File): InputStream?

    /**
     * Returns a reader for the given file if it exists, otherwise
     * returns null.
     */
    fun readerIfExists(file: File, charset: Charset): Reader?
}

interface WriteDisk {
    /**
     * Returns the root of this effect, relative to which every action
     * will be taken when referring to a path.
     */
    fun root(): Path?

    /**
     * Attempts to create the file described by the [path], relative to
     * the [root] if the path is relative. Returns the file if it was
     * successfully created, and null if another file already existed.
     */
    fun createFileIfNotExists(path: Path): File?

    /**
     * Attempts to delete the givne file, and returns whether anything was
     * deleted.
     */
    fun deleteFileIfExists(file: File): Boolean

    // TODO: mkdirs
}

interface AccessDisk : ReadDisk, WriteDisk

/**
 * The standard implementation for file access, which 
 */
class AccessDiskDefault(private val root: Path? = null) : AccessDisk {
    override fun root() = root
    override fun fileIfExists(path: Path): File? = path.toFile().takeIf { it.exists() }
    override fun inputStreamIfExists(file: File): InputStream? = file.takeIf { it.exists() }?.inputStream()
    override fun readerIfExists(file: File): Reader? = file.takeIf { it.exists() }?.reader()
    override fun createFileIfNotExists(path: Path): File? = path.toFile().takeIf { it.createNewFile() }
    override fun deleteFileIfExists(file: File): Boolean = file.delete()
}

class EphemeralFileSystem(private val root: Path) : AccessDisk {

}