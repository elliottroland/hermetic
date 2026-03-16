package hermetic.effects

import kotlinx.coroutines.delay

interface SuspendWait {
    suspend fun waitMillis(millis: Long)
}

class SuspendWaitDefault : SuspendWait {
    override suspend fun waitMillis(millis: Long) = delay(millis)
}

context(w: SuspendWait)
suspend fun waitMillis(millis: Long) = w.waitMillis(millis)
