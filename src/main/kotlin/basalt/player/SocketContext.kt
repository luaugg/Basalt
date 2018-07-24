package basalt.player

import basalt.server.BasaltServer
import io.undertow.websockets.core.WebSocketChannel
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import java.util.concurrent.atomic.AtomicLong

typealias PlayerMap = Object2ObjectOpenHashMap<String, BasaltPlayer>

class SocketContext internal constructor(val server: BasaltServer, val channel: WebSocketChannel, val userId: String) {
    internal val players = PlayerMap()
    internal val seq = AtomicLong(0)
}