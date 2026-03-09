package hermetic

import hermetic.either.*

fun <Fx> sayHelloFx(fx: Fx, name: String): Either<InvalidName, Unit>
where Fx : HasLog, Fx : HasTime = with(fx) {
    when {
        name.isEmpty() -> return err(InvalidName)
    }
    log.info("Hello, $name. It is now ${time.now()}")
    return ok(Unit)
}

data object InvalidName

interface HasLog {
    val log: Log
}

interface HasTime {
    val time: Time
}