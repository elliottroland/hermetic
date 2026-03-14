package hermetic.experiments

import hermetic.either.*

fun sayHelloSealed(fx: SayHelloRun, name: String): Either<InvalidName, Unit> = with(fx) {
    when {
        name.isEmpty() -> return err(InvalidName)
    }
    val log = effect(SayHelloEff.Log)
    val time = effect(SayHelloEff.Time)
    log.info("Hello, $name. It is now ${time.now()}")
    return ok(Unit)
}

sealed interface SayHelloEff<out Effect> {
    object Time : SayHelloEff<hermetic.experiments.Time>
    object Log : SayHelloEff<hermetic.experiments.Log>
    object Sleeper : SayHelloEff<hermetic.experiments.Sleeper>
}

interface SayHelloRun {
    fun <T : Effect> effect(e: SayHelloEff<T>): T
}