package hermetic.experiments

import hermetic.either.*
import java.time.Instant

interface Effect

interface Time : Effect {
    fun now(): Instant

    object Default : Time {
        override fun now() = Instant.now()
    }
}

interface Log : Effect {
    fun info(message: String)

    object Console : Log {
        override fun info(message: String) = println(message)
    }
}

interface Sleeper : Effect {
    fun sleep(millis: Long)

    object Default : Sleeper {
        override fun sleep(millis: Long) = Thread.sleep(millis)
    }
}

interface Sqs : Effect {
    sealed interface Error {
        data class QueueDoesNotExist(val id: String) : Error {
            constructor(name: QueueName) : this("name: $name")
            constructor(url: QueueUrl) : this("url: $url")
        }
    }

    fun getQueueUrl(name: QueueName): Either<Error, QueueUrl>
    fun getQueueAttributes(url: QueueUrl): Either<Error, QueueAttributes>

    data class QueueName(val value: String) {
        override fun toString() = value
    }
    data class QueueUrl(val value: String) {
        override fun toString() = value
    }
    data class QueueAttributes(val value: Map<String, String>) {
        override fun toString() = value.toString()
    }

    object Fake : Sqs {
        private val queueUrls = mapOf(
            "my-queue" to "https://my-queue"
        )
        private val queueAttrs = mapOf(
            "https://my-queue" to mapOf(
                "some-key" to "some-value"
            )
        )

        override fun getQueueUrl(name: QueueName): Either<Error, QueueUrl> =
            queueUrls[name.value]?.let { ok(QueueUrl(it)) }
                ?: err(Error.QueueDoesNotExist(name))

        override fun getQueueAttributes(url: QueueUrl): Either<Error, QueueAttributes> =
            queueAttrs[url.value]?.let { ok(QueueAttributes(it)) }
                ?: err(Error.QueueDoesNotExist(url))
    }
}