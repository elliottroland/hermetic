package hermetic.either

import hermetic.effects.*
import hermetic.effects.fs.*
import kotlin.io.writer

fun main() {
    val fs = GlobalFileSystem(null)
    val root = fs.getOrCreateDir("deferTest").getOrThrow()
    context(fs.restrictFs(root), WriteLogConsole()) {
        println(testDefer().getOrThrow())
    }
}

context(fs: FileSystem, _: WriteLog)
fun testDefer(): Either<String, Long> = either {
    val log = Log("testDefer")

    val dir = fs.getOrCreateDir("some-dir").getOrFail { "Failed to create dir: $it" }
    log.info("Created dir: $dir")
    defer {
        log.info("Deleting dir: $dir")
        fs.delete(dir).getOrFail { "Failed to clean up dir: $it" }
        fs.getDir(dir.path).getOrFail { "Failed to clean up dir: $it" }
    }

    val file = fs.createFile(dir.resolve("some-file")).getOrFail { "Failed to create file: $it" }
    log.info("Created file: $file")
    defer {
        log.info("Deleting file: $file")
        fs.delete(file).getOrFail { "Failed to clean up file: $it" }
    }

    val writer = file.writer().getOrFail { "Could not create writer: $it" }.deferClose()
    writer.deferClose().write("Hello, world!")

    log.info("File contents: ${file.reader().getOrThrow().readLines()}")
    file.sizeBytes
}



