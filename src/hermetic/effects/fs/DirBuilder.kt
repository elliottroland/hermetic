package hermetic.effects.fs

import hermetic.either.getOrThrow

class DirBuilderScope(private val fs: FileSystem<*, *>, private val root: Path? = null) {
    fun dir(name: String, mkdirs: Boolean = false, subBuilder: DirBuilderScope.(Dir) -> Unit): Dir =
        fs.getOrCreateDir(resolve(name), mkdirs).getOrThrow().also {
            DirBuilderScope(fs, it.path).subBuilder(it)
        }

    fun file(name: String, mkdirs: Boolean = false): File =
        fs.getOrCreateFile(resolve(name), mkdirs).getOrThrow()

    private fun resolve(name: String) =
        root?.resolve(name) ?: Path.of(name)
}

fun dirBuilder(fs: FileSystem<*, *>, subBuilder: DirBuilderScope.() -> Unit) {
    DirBuilderScope(fs, null).subBuilder()
}
