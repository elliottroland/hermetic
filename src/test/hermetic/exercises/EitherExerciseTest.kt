package hermetic.exercises

import hermetic.*
import kotlin.io.*
import kotlin.test.*
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.AfterAll

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EitherExerciseTest {
    val root = Path.of("build").resolve("EitherExercise")
    val fs = SimpleFileSystem()

    @BeforeAll
    fun setup() {
        root.java.toFile().mkdir()
        assertTrue(root.resolve("dir").java.toFile().mkdir())
        assertTrue(root.resolve("file").java.toFile().createNewFile())
    }

    @AfterAll
    fun cleanup() {
        root.java.toFile().deleteRecursively()
    }

    @Nested
    @DisplayName("getFile")
    inner class GetFile {
        @Test
        fun `fails if target does not exist`() {
            assertIs<PathDoesNotExist>(assertErr(fs.getFile(root.resolve("does-not-exist"))))
        }

        @Test
        fun `fails if target is dir`() {
            assertIs<PathIsDir>(assertErr(fs.getFile(root.resolve("dir"))))
        }

        @Test
        fun `returns file if it exists`() {
            assertEquals(File(root.resolve("file").java.toFile()), assertOk(fs.getFile(root.resolve("file"))))
        }
    }

    @Nested
    @DisplayName("getDir")
    inner class GetDir {
        @Test
        fun `fails if target does not exist`() {
            assertIs<PathDoesNotExist>(assertErr(fs.getDir(root.resolve("does-not-exist"))))
        }

        @Test
        fun `fails if target is file`() {
            assertIs<PathIsFile>(assertErr(fs.getDir(root.resolve("file"))))
        }

        @Test
        fun `returns dir if it exists`() {
            assertEquals(Dir(root.resolve("dir").java.toFile()), assertOk(fs.getDir(root.resolve("dir"))))
        }
    }

    @Nested
    @DisplayName("createFile")
    inner class CreateFile {
        @Test
        fun `fails if the target exists as a file or dir`() {
            assertIs<PathIsFile>(assertErr(fs.createFile(root.resolve("file"))))
            assertIs<PathIsDir>(assertErr(fs.createFile(root.resolve("dir"))))
        }

        @Test
        fun `fails if a parent dir does not exist, or is a file`() {
            assertIs<ParentPathDoesNotExist>(assertErr(fs.createFile(root.resolve("non-existent").resolve("file"))))
            assertIs<AncestorPathIsFile>(assertErr(fs.createFile(root.resolve("file").resolve("child-file"))))
        }

        @Test
        fun `succeeds if does not exist and can be created, along with parents if mkdirs = true`() {
            assertOk(fs.createFile(root.resolve("dir").resolve("create-file")))
            assertOk(fs.createFile(root.resolve("create-file-dir").resolve("create-file"), mkdirs = true))
        }
    }

    @Nested
    @DisplayName("getOrCreateFile")
    inner class GetOrCreateFile {
        @Test
        fun `returns file if it already exists`() {
            assertOk(fs.getOrCreateFile(root.resolve("file")))
        }

        @Test
        fun `fails if target exists as a dir`() {
            assertIs<PathIsDir>(assertErr(fs.getOrCreateFile(root.resolve("dir"))))
        }

        @Test
        fun `fails if a parent dir does not exist, or is a file`() {
            assertIs<ParentPathDoesNotExist>(assertErr(fs.getOrCreateFile(root.resolve("non-existent").resolve("file"))))
            assertIs<AncestorPathIsFile>(assertErr(fs.getOrCreateFile(root.resolve("file").resolve("child-file"))))
        }

        @Test
        fun `succeeds if does not exist and can be created, along with parents if mkdirs = true`() {
            assertOk(fs.getOrCreateFile(root.resolve("dir").resolve("get-or-create-file")))
            assertOk(fs.getOrCreateFile(root.resolve("get-or-create-file-dir").resolve("get-or-create-file"), mkdirs = true))
        }
    }

    @Nested
    @DisplayName("getOrCreateDir")
    inner class GetOrCreateDir {
        @Test
        fun `returns dir if it already exists`() {
            assertOk(fs.getOrCreateDir(root.resolve("dir")))
        }

        @Test
        fun `fails if target exists as a file`() {
            assertIs<PathIsDir>(assertErr(fs.getOrCreateDir(root.resolve("file"))))
        }

        @Test
        fun `fails if a parent dir does not exist, or is a file`() {
            assertIs<ParentPathDoesNotExist>(assertErr(fs.getOrCreateDir(root.resolve("non-existent").resolve("file"))))
            assertIs<AncestorPathIsFile>(assertErr(fs.getOrCreateDir(root.resolve("file").resolve("child-dir"))))
        }

        @Test
        fun `succeeds if does not exist and can be created, along with parents if mkdirs = true`() {
            assertOk(fs.getOrCreateDir(root.resolve("dir").resolve("get-or-create-dir")))
            assertOk(fs.getOrCreateDir(root.resolve("get-or-create-dir-dir").resolve("get-or-create-dir"), mkdirs = true))
        }
    }
}