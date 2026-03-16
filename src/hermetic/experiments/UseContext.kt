package hermetic.experiments

import hermetic.either.Either
import hermetic.either.err
import hermetic.either.ok

context(time: Time, log: Log)
fun sayHelloContext(name: String): Either<InvalidName, Unit> {
    when {
        name.isEmpty() -> return err(InvalidName)
    }
    log.info("Hello, $name. It is now ${time.now()}")
    return ok(Unit)
}
