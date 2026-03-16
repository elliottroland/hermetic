package hermetic.effects.fs

import hermetic.either.err
import hermetic.either.getOrThrow
import hermetic.test.assertErr
import hermetic.test.assertOk
import hermetic.useThrowing
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.assertEquals
import kotlin.test.assertIs

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DefaultFileSystemTest {
    private lateinit var rfs: RestrictedFileSystem

    @BeforeAll
    fun setup() {
        val gfs = FileSystem.global()
        val root = gfs.getOrCreateDir("build/DefaultFileSystemTest").getOrThrow()
        rfs = gfs.restricted(root)
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterAll
    fun cleanup() {
        rfs.rootDir().path.java.deleteRecursively()
    }

    @Test
    fun `createDir should fail if one its parents is a file`() {
        rfs.ephemeral().useThrowing { fs ->
            val dir1 = assertOk(fs.createDir("dir1"))
            val file2 = assertOk(fs.createFile(dir1.resolve("file2")))
            val result = fs.createDir(file2.path.resolve("dir3"), mkdirs = true)
            assertEquals(err(AncestorPathIsFile(file2, file2.path.resolve("dir3"))), result)
        }
    }

    @Test
    fun `createDir should create all parent dirs successfully if mkdirs is true, but fail otherwise`() {
        rfs.useThrowing { fs ->
            val path = fs.rootDir().resolve("some/random/dir/many/levels/down")
            val err = assertErr(fs.createDir(path))
            assertEquals(ParentPathDoesNotExist(path), err)

            val dir = assertOk(fs.createDir(path, mkdirs = true))
            assertEquals(Dir(path.java.toFile()), dir)
        }
    }

    @Test
    fun `createDir should fail if the path already points to a dir or file`() {
        rfs.useThrowing { fs ->
            assertOk(fs.createDir("dir"))
            assertIs<PathIsDir>(assertErr(fs.createDir("dir")))

            assertOk(fs.createFile("file"))
            assertIs<PathIsFile>(assertErr(fs.createDir("file")))
        }
    }
}
