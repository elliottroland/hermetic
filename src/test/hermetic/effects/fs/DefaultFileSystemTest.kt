package hermetic.effects.fs

import hermetic.effects.Async
import hermetic.either.err
import hermetic.either.getOrThrow
import hermetic.test.assertErr
import hermetic.test.assertOk
import hermetic.test.test
import hermetic.throwIfNotNull
import hermetic.useThrowing
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.platform.reporting.shadow.org.opentest4j.reporting.events.core.CoreFactory.result
import java.io.InputStream
import java.io.OutputStream
import kotlin.io.resolve
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertIsNot

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DefaultFileSystemTest {
    private val fs = FileSystem.test(this)

    @AfterEach
    fun shouldBeEmpty() {
        assertEquals(emptyList(), fs.list(fs.rootDir()).toList())
    }

    @AfterAll
    fun cleanup() {
        fs.finalize().throwIfNotNull()
    }

    @Nested
    @DisplayName("get")
    inner class Get {
        @Test
        fun `fails if the target is not there or parent path does not exist`() {
            assertIs<PathDoesNotExist>(assertErr(fs.get("some-random-target")))
            assertIs<PathDoesNotExist>(assertErr(fs.get("some/dir/some-random-target")))
        }

        @Test
        fun `returns the file or dir if it is there`() {
            dirBuilder(fs.ephemeral(), finalize = true) {
                val dir = dir("some-dir")
                assertEquals(dir, assertOk(get(dir.path)))

                val file = file("some-file")
                assertEquals(file, assertOk(get(file.path)))
            }
        }
    }

    @Nested
    @DisplayName("createDir")
    inner class CreateDir {
        @Test
        fun `fails if one its parents is a file`() {
            dirBuilder(fs.ephemeral(), finalize = true) {
                dir("dir") {
                    val file = file("file")
                    val badDirPath = file.path.resolve("bad-dir")
                    val result = createDir(badDirPath, mkdirs = true)
                    assertEquals(err(AncestorPathIsFile(file, badDirPath)), result)
                }
            }
        }

        @Test
        fun `creates all parent dirs successfully if mkdirs is true, but fail otherwise`() {
            dirBuilder(fs.ephemeral(), finalize = true) {
                val path = rootDir().resolve("some/random/dir/many/levels/down")
                val err = assertErr(createDir(path))
                assertEquals(ParentPathDoesNotExist(path), err)

                val dir = assertOk(createDir(path, mkdirs = true))
                assertEquals(Dir(path.java.toFile()), dir)
            }
        }

        @Test
        fun `fails if the path already points to a dir or file`() {
            dirBuilder(fs.ephemeral(), finalize = true) {
                dir("dir")
                assertIs<PathIsDir>(assertErr(fs.createDir("dir")))

                file("file")
                assertIs<PathIsFile>(assertErr(fs.createDir("file")))
            }
        }
    }

    @Nested
    @DisplayName("createFile")
    inner class CreateFile {
        @Test
        fun `fails if one its parents is a file`() {
            dirBuilder(fs.ephemeral(), finalize = true) {
                dir("dir") {
                    val file = file("file")
                    val badDirPath = file.path.resolve("bad-dir")
                    val result = createFile(badDirPath, mkdirs = true)
                    assertEquals(err(AncestorPathIsFile(file, badDirPath)), result)
                }
            }
        }

        @Test
        fun `creates all parent dirs successfully if mkdirs is true, but fail otherwise`() {
            dirBuilder(fs.ephemeral(), finalize = true) {
                val path = rootDir().resolve("some/random/dir/many/levels/down")
                val err = assertErr(createFile(path))
                assertEquals(ParentPathDoesNotExist(path), err)

                val dir = assertOk(createFile(path, mkdirs = true))
                assertEquals(File(path.java.toFile()), dir)
            }
        }

        @Test
        fun `fails if the path already points to a dir or file`() {
            dirBuilder(fs.ephemeral(), finalize = true) {
                dir("dir")
                assertIs<PathIsDir>(assertErr(fs.createFile("dir")))

                file("file")
                assertIs<PathIsFile>(assertErr(fs.createFile("file")))
            }
        }
    }

    @Nested
    @DisplayName("delete")
    inner class Delete {
        @Test
        fun `fails if target does not exist, or if parent is file`() {
            dirBuilder(fs.ephemeral(), finalize = true) {
                val dir = dir("dir")
                assertOk(delete(dir))
                assertIs<PathDoesNotExist>(assertErr(delete(dir)))

                val file = file("file")
                val badDir = Dir(file.path.resolve("bad-dir").java.toFile())
                assertIs<PathDoesNotExist>(assertErr(delete(badDir)))
            }
        }

        @Test
        fun `fails if path still exists after trying to delete target`() {
            dirBuilder(fs.ephemeral(), finalize = true) {
                val dir = dir("dir-with-children") {
                    file("file")
                }
                assertIs<PathStillExists>(assertErr(fs.delete(dir)))
            }
        }
    }

    @Nested
    @DisplayName("inputStream and outputStream")
    inner class InputStreamAndOutputStream {
        @Test
        fun `returns an async version if and only if FS is async`() {
            dirBuilder(fs.ephemeral(), finalize = true) {
                val file = file("file")
                assertIsNot<AsyncInputStream>(assertIs<InputStream>(assertOk(inputStream(file))))
                assertIsNot<AsyncOutputStream>(assertIs<OutputStream>(assertOk(outputStream(file))))
            }

            dirBuilder(fs.ephemeral().async(Async.IO), finalize = true) {
                val file = file("file")
                assertIs<AsyncInputStream>(assertOk(inputStream(file)))
                assertIs<AsyncOutputStream>(assertOk(outputStream(file)))
            }
        }

        @Test
        fun `fails if target file does not exist`() {
            val file = File(fs.rootDir().resolve("non-existent-file").java.toFile())
            assertIs<PathDoesNotExist>(assertErr(fs.inputStream(file)))
            assertIs<PathDoesNotExist>(assertErr(fs.outputStream(file)))
        }
    }

    @Nested
    @DisplayName("walk")
    inner class Walk {
        fun DirBuilderScope<*, *>.setup() {
            dir("dir1") {
                dir("dir1.1") {
                    file("file1.1.1")
                    file("file1.1.2")
                }
                dir("dir1.2") {
                    file("file1.2.1")
                }
            }
            dir("dir2") {
                file("file2.1")
            }
        }

        @Test
        fun `honors shouldEnter`() {
            dirBuilder(fs.ephemeral(), finalize = true) {
                setup()
                assertEquals(
                    listOf("dir2", "file2.1"),
                    walk(assertOk(getDir("dir2"))) { it.name.startsWith("dir2") }.map { it.name }.toList()
                )
            }
        }

        @Test
        fun `honors maxDepth`() {
            dirBuilder(fs.ephemeral(), finalize = true) {
                setup()
                assertEquals(
                    listOf("dir1", "dir1.1", "dir1.2"),
                    walk(assertOk(getDir("dir1")), maxDepth = 1).map { it.name }.toList()
                )
            }
        }
    }
}
