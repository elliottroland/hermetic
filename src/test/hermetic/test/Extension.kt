package hermetic.test

import hermetic.effects.fs.EphemeralRestrictedFileSystem
import hermetic.effects.fs.FileSystem
import hermetic.effects.fs.restricted
import hermetic.effects.fs.rootDir
import hermetic.either.getOrThrow

fun FileSystem.Companion.test(test: Any): EphemeralRestrictedFileSystem {
    val rfs = FileSystem.global().restricted("build").getOrThrow()
    rfs.rootDir().resolve(test::class.simpleName!!).java.toFile().deleteRecursively()
    return rfs.ephemeral().restricted(test::class.simpleName!!).getOrThrow()
}
