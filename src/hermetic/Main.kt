package hermetic

fun main() {
    // Fx
    val fx = object : HasLog, HasTime {
        override val log = Log.Console
        override val time = Time.Default
    }
    sayHelloFx(fx, "Roland").onErr { err -> println("Failed with: $err") }

    // Sealed
    val run = object : SayHelloRun {
        @Suppress("UNCHECKED_CAST")
        override fun <T : Effect> effect(e: SayHelloEff<T>): T =
            when (e) {
                is SayHelloEff.Time -> Time.Default
                is SayHelloEff.Log -> Log.Console
                is SayHelloEff.Sleeper -> Sleeper.Default
            } as T
    }
    sayHelloSealed(run, "Roland").onErr { err -> println("Failed with: $err") }

    // Context
    context(Time.Default, Log.Console) {
        sayHelloContext("Roland").onErr { err -> println("Failed with: $err") }
    }

    // Receivers
    val ctx = object : Eff.Timing.Default, Eff.Logging.Console, Eff.LogErrors.Console {}
    val greeter = Greeter()
    greeter.sayHelloReceivers(ctx, "Roland")
    context(ctx) { // NOTE: don't need to use ctx like this, just convenience
        greeter.sayHelloCtx("Roland")
    }

    // Hermetic
    val hfx = object : Logging by Logging.Console(), CheckTime by CheckTime.Default() {}
    val hgreeter = GreeterHermetic()
    hgreeter.sayHelloFx(hfx, "Roland")
    hgreeter.sayHelloTuple(Pair(CheckTime.Default(), Logging.Console()), "Roland")
    context(hfx) {
        hgreeter.sayHelloCtx("Roland")
    }

    // SOP
    val log = SubjectiveLogger("Logger")
    with(Reluctant) {
        log.info("this is some message")
    }
    with(Enthusiastic) {
        log.info("this is some message")
    }
}
