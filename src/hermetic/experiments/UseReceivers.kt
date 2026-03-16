package hermetic.experiments

import hermetic.either.Either
import hermetic.either.err
import hermetic.either.ok
import java.time.Instant

class Greeter {
    val log = Eff.Logger(this::class.simpleName!!)
    val time = Eff.Time

    fun <Fx> sayHelloReceivers(fx: Fx, name: String): Either<InvalidName, Unit>
    where Fx : Eff.Timing, Fx : Eff.Logging, Fx : Eff.LogErrors = with(fx) {
        when {
            name.isEmpty() -> return err(InvalidName)
        }
        log.info("Hello $name, the time is ${time.now()}")
        log.error("This is an error", null)
        return ok(Unit)
    }

    context(_: Eff.Logging, t: Eff.Timing)
    fun sayHelloCtx(name: String): Either<InvalidName, Unit> {
        when {
            name.isEmpty() -> return err(InvalidName)
        }
        log.info("Hello $name, the time is ${time.now()}")
        return ok(Unit)
    }
}

object Eff {
    class Logger(val name: String)
    object Time

    interface Timing {
        fun Time.now(): Instant

        interface Default : Timing {
            override fun Time.now() = Instant.now()
        }
    }

    interface Logging {
        fun Logger.info(message: String)

        interface Console : Logging {
            override fun Logger.info(message: String) = println("[INFO] $name: $message")
        }
    }

    interface LogErrors {
        fun Logger.error(message: String, cause: Throwable?)

        interface Console : LogErrors {
            override fun Logger.error(message: String, cause: Throwable?) = println("[ERROR] $name: $message, $cause")
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
context(l: Eff.Logging)
inline fun Eff.Logger.info(message: String) = with(l) { info(message) }

@Suppress("NOTHING_TO_INLINE")
context(t: Eff.Timing)
inline fun Eff.Time.now() = with(t) { now() }
