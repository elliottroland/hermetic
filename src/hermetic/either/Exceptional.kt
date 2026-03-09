package hermetic.either

/**
 * When using [Either] we are able to model failures as any type. However, it can be useful
 * to know when specific failure types actually encode a [Throwable], so that for example
 * logging can be enriched.
 */
interface Exceptional {
    val exception: Throwable?
}