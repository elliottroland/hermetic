package hermetic.effects.fs

import java.io.*
import java.time.Instant
import java.io.File as JFile
import java.nio.file.Path as JPath

sealed interface FileOrDir {
    val file: JFile
    val path get() = file.toPath()
    val exists get() = file.exists()
    val name get() = file.name
    val readable get() = file.canRead()
    val writeable get() = file.canWrite()
    val lastModified get() = Instant.ofEpochMilli(file.lastModified())

    fun fileOrNull(): File? = this as? File
    fun dirOrNull(): Dir? = this as? Dir
}

data class File(override val file: JFile) : FileOrDir {
    val extension get() = file.extension
    val executable get() = file.canExecute()
    val sizeBytes get() = file.length()

    context(fs: FileSystem)
    fun inputStream() = fs.inputStream(this)
    
    context(fs: FileSystem)
    fun outputStream() = fs.outputStream(this)
}

data class Dir(override val file: JFile) : FileOrDir {
    context(fs: FileSystem)
    fun files() = listFiles(this)

    context(fs: FileSystem)
    fun dirs() = listDirs(this)
}