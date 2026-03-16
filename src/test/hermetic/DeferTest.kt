package hermetic

import hermetic.*
import kotlin.test.*
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeferTest {
    @Test
    fun `deferred blocks run in reverse order after the rest of the block`() {
        val actions = mutableListOf<String>()
        defers {
            actions.add("block1")
            defer { actions.add("defer1") }

            actions.add("block2")
            defer { actions.add("defer2") }
        }
        assertEquals(listOf("block1", "block2", "defer2", "defer1"), actions)
    }

    @Test
    fun `deferred errors are rethrown after all have run`() {
        val actions = mutableListOf<String>()
        try {
            defers {
                actions.add("block1")
                defer {
                    actions.add("defer1")
                    throw throwable("error1")
                }

                actions.add("block2")
                defer {
                    actions.add("defer2")
                    throw throwable("error2")
                }
            }
        } catch (e: Err) {
            assertEquals("error2", e.message)
        }
        assertEquals(listOf("block1", "block2", "defer2", "defer1"), actions)
    }

    @Test
    fun `deferred blocks run when rest of the block fails`() {
        val actions = mutableListOf<String>()
        try {
            defers {
                actions.add("block1")
                defer {
                    actions.add("defer1")
                    throw throwable("error1")
                }

                actions.add("block2")
                defer {
                    actions.add("defer2")
                    throw throwable("error2")
                }

                throw throwable("error")

                actions.add("block3")
                defer {
                    actions.add("defer3")
                }
            }
        } catch (e: Err) {
            assertEquals("error", e.message)
        }
        assertEquals(listOf("block1", "block2", "defer2", "defer1"), actions)
    }
}