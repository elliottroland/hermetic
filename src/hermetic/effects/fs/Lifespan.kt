package hermetic.effects.fs

import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Defines how a [FileSystem] will handle the lifespan of the [File]s and [Dir]s it is used to create. This governs whether files
 * and directories created using this file system will be deleted automatically: in the [Persistent] lifespan nothing will be
 * deleted automatically, while in the [Ephemeral] lifespan all files and directories created using the relevant file system will
 * be deleted (in reverse order) when that file system is [finalized][FileSystem.finalize].
 */
sealed interface Lifespan {
    /**
     * Registers the [FileOrDir] with the current file system, so that the file system can do something with them later.
     */
    fun register(ford: FileOrDir)

    /**
     * Returns the list of registered [FileOrDir]s. There is no guarantee that every [FileOrDir] that was passed to [register]
     * is returned by this.
     */
    fun registered(): Collection<FileOrDir>

    /**
     * A [Lifespan] which does nothing with [File]s and [Dir]s after they are created. Any cleanup must be done manually by
     * the user of the file system. This is the default behavior of file systems.
     */
    object Persistent : Lifespan {
        override fun register(ford: FileOrDir): Unit = Unit
        override fun registered(): Collection<FileOrDir> = emptyList()
    }

    /**
     * A [Lifespan] which tracks all [File]s and [Dir]s that are created, so that they can be deleted (in reverse order) when
     * the file system is [finalized][FileSystem.finalize].
     */
    class Ephemeral : Lifespan {
        private val registered = ConcurrentLinkedDeque<FileOrDir>()
        override fun register(ford: FileOrDir): Unit = registered.addFirst(ford)
        override fun registered(): Collection<FileOrDir> = registered
    }
}
