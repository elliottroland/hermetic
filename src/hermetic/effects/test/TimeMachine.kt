package hermetic.effects.test

import hermetic.effects.*
import java.time.*

class TimeMachine(private var instant: Instant = Instant.now()) : ReadTime, Wait {
    override fun now() = instant

    override fun waitMillis(millis: Long) {
        instant += Duration.ofMillis(millis)
        // We wait a little bit to make sure we don't burn a hole in the CPU
        Thread.sleep(50)
    }
}

fun main() {
    val log = Log("effects.test.TimeMachine")

    context(WriteLogConsole(), TimeMachine()) {
        log.info("With time machine")
        f()
    }

    context(WriteLogConsole(), ReadTimeDefault(), WaitDefault()) {
        log.info("")
        log.info("Without time machine")
        f()
    }
}

context(_: WriteLog, _: ReadTime, _: Wait)
fun f() {
    log.info("Time before wait: ${now()}")
    waitMillis(10_000)
    log.info("Time after wait: ${now()}")
}