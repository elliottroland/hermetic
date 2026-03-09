package hermetic.effects

interface Wait {
    fun waitMillis(millis: Long)
}

class WaitDefault : Wait {
    override fun waitMillis(millis: Long) = Thread.sleep(millis)
}

context(w: Wait)
fun waitMillis(millis: Long) = w.waitMillis(millis)