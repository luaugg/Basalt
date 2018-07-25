package basalt.messages.server

@Suppress("UNUSED")
class PlayerUpdate internal constructor(val guildId: String, val position: Long, val timestamp: Long) {
    val op = "playerUpdate"
}