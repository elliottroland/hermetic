package hermetic

import hermetic.*
import kotlin.test.*
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeferTest {
    @Test
    fun `deferred blocks run after the rest of the block and in reverse order`() {
        val actions = mutableListOf<String>()
        defers {
            actions.add("block1")
            defer { actions.add("defer1") }

            actions.add("block2")
            defer { actions.add("defer2") }
        }
        assertEquals(listOf("block1", "block2", "defer2", "defer1"), actions)
    }
}