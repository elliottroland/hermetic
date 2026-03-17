package hermetic.effects.fs

/**
 * Defines how a [FileSystem] is scoped to a particular directory. Primarily, this governs how relative [Path]s are
 * resolved in the context of that file system: in the [Global] scope they are resolved relative to the current working
 * directory, whereas in the [Restricted] scope they are resolved relative to the [rootDir][Restricted.rootDir].
 */
sealed interface Scope {
    /**
     * Interprets the [path] within the current scope. This may throw an exception if the given path is invalid in the
     * current scope. This can happen, for example, when trying to resolve a file outside the root directory of a
     * [Restricted] scope.
     */
    fun resolve(path: Path): Path

    /**
     * Ensures that the given [FileOrDir] is within the current scope, and rewrites it's path accordingly. This might throw
     * an exception if the [FileOrDir] was constructed outside the current [Restricted] scope.
     */
    fun <Ford : FileOrDir> resolve(ford: Ford): Ford

    /**
     * A [Scope] which interprets all relative paths as relative to the current working directory. This will not throw any
     * exceptions when resolving [Path]s or [FileOrDir]s.
     */
    object Global : Scope {
        override fun resolve(path: Path): Path = path
        override fun <T : FileOrDir> resolve(ford: T): T = ford
    }

    /**
     * A [Scope] which interprets all relative paths as relative to the [rootDir].
     */
    class Restricted(val rootDir: Dir) : Scope {
        /**
         * Converts the [path] to something starting with the [rootDir]'s path.
         * @throws IllegalArgumentException If the given [path] resolves to something outside the [rootDir].
         */
        override fun resolve(path: Path): Path {
            return when {
                path.startsWith(rootDir.path) -> return path
                path.isAbsolute -> rootDir.path.relativize(path)
                else -> path
            }.also {
                require(!it.startsWith("..")) { "Expected path ${path.absolute} to be below root ${rootDir.path.absolute}" }
            }.let { rootDir.path.resolve(it) }
        }

        /**
         * Ensures that the given [FileOrDir] exists within the scope of the [rootDir], possibly re-writing it
         * to make this clear.
         * @throws IllegalArgumentException If the given [FileOrDir] resolves to something outside the [rootDir].
         */
        @Suppress("UNCHECKED_CAST")
        override fun <T : FileOrDir> resolve(ford: T): T =
            if (ford.path.java.normalize().startsWith(rootDir.path.java)) {
                ford
            } else {
                val path = resolve(ford.path.absolute)
                when (ford) {
                    is File -> File(path.java.toFile())
                    is Dir -> Dir(path.java.toFile())
                } as T
            }
    }
}
