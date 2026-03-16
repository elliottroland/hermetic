package hermetic.test

import hermetic.either.Either
import hermetic.either.errOr
import hermetic.either.getOr
import kotlin.test.assertTrue

fun <E, O> assertOk(either: Either<E, O>): O {
    assertTrue("Expected an ok(), but found $either") { either.isOk }
    return either.getOr { throw IllegalStateException("Should not be possible to get here") }
}

fun <E, O> assertErr(either: Either<E, O>): E {
    assertTrue("Expected an err(), but found $either") { either.isErr }
    return either.errOr { throw IllegalStateException("Should not be possible to get here") }
}
