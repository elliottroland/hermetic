package hermetic.effects

import java.util.UUID
import kotlin.random.Random as KRandom

// TODO: Do we want to duplicate all these functions?

interface Random {
    val random: KRandom // because I'm lazy
    abstract fun randomUUID(): UUID
}

context(r: Random)
fun randomInt() = r.random.nextInt()

context(r: Random)
fun randomUUID() = r.randomUUID()

class RandomDefault : Random {
    override val random = KRandom.Default
    override fun randomUUID() = UUID.randomUUID()
}

fun main() {
    context(RandomDefault(), LoggingConsole()) {
        f()
    }
}

context(_: Logging, r: Random)
fun f() {
    val log = Log("effects.Random")
    log.info("Random number: ${randomInt()}")
    log.info("Random UUID: ${randomUUID()}")
}