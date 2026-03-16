package hermetic.test

import hermetic.effects.Log
import hermetic.effects.Logging
import hermetic.effects.LoggingConsole
import hermetic.effects.ReadTime
import hermetic.effects.ReadTimeDefault
import hermetic.effects.Wait
import hermetic.effects.WaitDefault
import hermetic.effects.log
import hermetic.effects.now
import hermetic.effects.waitMillis
import java.time.Duration
import java.time.Instant

class TimeMachine(private var instant: Instant = Instant.now()) : ReadTime, Wait {
    override fun now() = instant

    override fun waitMillis(millis: Long) {
        instant += Duration.ofMillis(millis)
        // We wait a little bit to make sure we don't burn a hole in the CPU
        Thread.sleep(50)
    }
}
