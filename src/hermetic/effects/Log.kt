package hermetic.effects

import hermetic.either.*
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

interface Logging {
    fun write(log: Log, level: Log.Level, message: String?, error: Throwable?)
}

data class Log(val name: String) {
    context(logger: Logging)
    fun info(message: Any) =
        logger.write(this, Log.Level.INFO, message.toString(), null)

    context(logger: Logging)
    fun error(message: Any? = null, exception: Throwable? = null) =
        if (message is Throwable) {
            error(message)
        } else {
            logger.write(this, Log.Level.ERROR, message?.toString(), exception)
        }

    context(logger: Logging)
    fun error(failure: Throwable) =
        logger.write(this, Log.Level.ERROR, null, failure)

    enum class Level { DEBUG, INFO, WARN, ERROR }
    companion object {
        fun from(a: Any): Log = Log(a::class.simpleName ?: "UNKNOWN")
    }
}

class LoggingConsole : Logging {
    override fun write(log: Log, level: Log.Level, message: String?, error: Throwable?) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        pw.print("${Instant.now()} [${level.name}] ${log.name} ")
        if (message != null) {
            pw.print(message)
        }
        if (error != null) {
            if (message != null) {
                pw.println("")
            }
            error.printStackTrace(pw)
        }
        pw.flush()

        if (level >= Log.Level.ERROR) {
            System.err.println(sw.toString())
        } else {
            println(sw.toString())
        }
    }
}