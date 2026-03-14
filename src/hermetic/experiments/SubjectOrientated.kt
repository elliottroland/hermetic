package hermetic.experiments

class SubjectiveLogger(val name: String)

object Reluctant {
    fun SubjectiveLogger.info(message: String) {
        println("$name: I'm not sure I should say this, but $message")
    }
}

object Enthusiastic {
    fun SubjectiveLogger.info(message: String) {
        println("$name: Oh my word, did you hear that $message!")
    }
}