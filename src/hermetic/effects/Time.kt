package hermetic.effects

import java.time.Duration
import java.time.Instant

interface ReadTime {
    fun now(): Instant
}

context(t: ReadTime)
fun now(): Instant = t.now()

context(t: ReadTime)
fun Duration.beforeNow(): Instant = t.now() - this

context(t: ReadTime)
fun Duration.ago() = beforeNow()

context(t: ReadTime)
fun Duration.afterNow(): Instant = t.now() + this

class ReadTimeDefault : ReadTime {
    override fun now() = Instant.now()
}
