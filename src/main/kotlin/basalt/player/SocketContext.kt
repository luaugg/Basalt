package basalt.player

import io.vertx.core.http.ServerWebSocket

class SocketContext(val userId: String, var webSocket: ServerWebSocket) {
    private val players = HashMap<String, BasaltPlayer>()
}