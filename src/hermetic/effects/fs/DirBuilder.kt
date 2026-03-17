package hermetic.effects.fs

import hermetic.either.getOrThrow

class DirBuilderScope<L : Lifespan, S : Scope>(private val fs: FileSystem<L, S>, private val root: Path? = null) : FileSystem<L, S> by fs {
    fun dir(name: String, mkdirs: Boolean = false, subBuilder: DirBuilderScope<L, S>.(Dir) -> Unit = {}): Dir =
        getOrCreateDir(resolve(name), mkdirs).getOrThrow().also {
            DirBuilderScope(fs, it.path).subBuilder(it)
        }

    fun file(name: String, mkdirs: Boolean = false): File =
        getOrCreateFile(resolve(name), mkdirs).getOrThrow()

    private fun resolve(name: String) =
        root?.resolve(name) ?: Path.of(name)
}

fun <L : Lifespan, S : Scope> dirBuilder(fs: FileSystem<L, S>, finalize: Boolean = false, subBuilder: DirBuilderScope<L, S>.() -> Unit): FinalizationError? {
    var finalizeError: FinalizationError? = null
    try {
        DirBuilderScope(fs, null).subBuilder()
    } finally {
        if (finalize) {
            finalizeError = fs.finalize()
        }
    }
    return finalizeError
}
