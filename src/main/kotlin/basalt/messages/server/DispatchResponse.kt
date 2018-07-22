package basalt.messages.server

import basalt.player.SocketContext
import com.jsoniter.annotation.JsonIgnore

@Suppress("UNUSED")
class DispatchResponse internal constructor(@field:JsonIgnore private val context: SocketContext,
                                            val guildId: String? = null, val name: String, val data: Any? = null) {
    val op = "dispatch"
    val seq = context.seq.get()
}