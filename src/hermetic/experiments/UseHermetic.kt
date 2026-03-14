package hermetic.experiments

import hermetic.either.*
import java.time.Instant

class GreeterHermetic {
    val log = Logging.Adapter(this::class.simpleName!!)
    
    fun <Fx> sayHelloFx(fx: Fx, name: String): Either<InvalidName, Unit>
    where Fx : CheckTime, Fx : Logging = with(fx) {
        when {
            name.isEmpty() -> return err(InvalidName)
        }
        log.info("Hello, $name. It is now ${now()}")
        return ok(Unit)
    }

    fun sayHelloTuple(fx: Pair<CheckTime, Logging>, name: String): Either<InvalidName, Unit> {
        val (time, logging) = fx
        with (logging) {
            when {
                name.isEmpty() -> return err(InvalidName)
            }
            log.info("Hello, $name. It is now ${time.now()}")
            return ok(Unit)
        }
    }

    context(_: Logging, time: CheckTime)
    fun sayHelloCtx(name: String): Either<InvalidName, Unit> {
        when {
            name.isEmpty() -> return err(InvalidName)
        }
        log.info("Hello, $name. It is now ${time.now()}")
        return ok(Unit)
    }
}

data class Tuple3<A, B, C>(val a: A, val b: B, val c: C)
data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

// Effects should define only functions. Bare particulars can be used to create object-like behaviors
// with static dispatching.

interface CheckTime {
    fun now(): Instant

    class Default : CheckTime {
        override fun now() = Instant.now()
    }
}

interface Logging {
    fun writeLog(adapter: Adapter, message: String, level: Level)

    enum class Level { DEBUG, INFO, WARN, ERROR }

    // Defines stateful information which is ideally computed once and re-used
    // by whatever implements Logging
    class Adapter(val name: String, val level: Level = Level.DEBUG) {
        // When using context-style, then you can define them here
        context(l: Logging)
        fun info(message: String) = l.writeLog(this, message, Level.INFO)
    }

    // When using Fx-style, then you have to define all the bits here
    fun Adapter.info(message: String) = writeLog(this, message, Level.INFO)

    class Console : Logging {
        override fun writeLog(adapter: Adapter, message: String, level: Level) =
            println("[${level.name}] ${adapter.name}: $message")
    }
}
