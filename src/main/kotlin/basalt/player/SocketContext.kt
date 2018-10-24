package basalt.player

import io.vertx.core.http.ServerWebSocket

class SocketContext(val userId: String, var webSocket: ServerWebSocket) {
    val players = HashMap<String, BasaltPlayer>()
    var resumeTimer: Long? = null
}