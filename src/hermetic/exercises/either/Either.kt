package hermetic.exercises.either

import hermetic.effects.fs.FileSystem
import hermetic.effects.fs.getDir
import hermetic.either.getOrThrow

fun main() {
    val fs = FileSystem.global()
    var file: java.io.File? = fs.getDir(".").getOrThrow().java.canonicalFile
    do {
        println(file)
        file = file?.parentFile
    } while (file != null)
}
