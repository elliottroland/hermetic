package hermetic.exercises

import hermetic.either.*
import kotlin.test.*

fun <E, O> assertOk(either: Either<E, O>): O =
    either.getOr { fail("Expected either to be ok() but it was err($it)") }

fun <E, O> assertErr(either: Either<E, O>): E =
    either.errOr { fail("Expected either to be err() but it was ok($it)") }