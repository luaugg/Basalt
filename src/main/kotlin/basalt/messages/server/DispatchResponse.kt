package basalt.messages.server

@Suppress("UNUSED")
class DispatchResponse(val op: String = "dispatch", val guildId: String? = null, val name: String, val data: Any? = null)