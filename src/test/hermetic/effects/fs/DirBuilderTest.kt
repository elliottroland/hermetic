package hermetic.effects.fs

import hermetic.either.getOrThrow
import hermetic.test.assertOk
import hermetic.test.test
import hermetic.useThrowing
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DirBuilderTest {
    @Test
    fun `creates directories and files using the underlying file system`() {
        FileSystem.test(this).useThrowing { fs ->
            val root = fs.getOrCreateDir("DirBuilderTest").getOrThrow()
            dirBuilder(fs.restricted(root)) {
                dir("some-dir") {
                    file("some-file")
                }
            }
            assertOk(fs.getDir(root.resolve("some-dir")))
            assertOk(fs.getFile(root.resolve("some-dir/some-file")))
        }
    }
}
