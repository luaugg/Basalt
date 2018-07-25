package basalt.player

import basalt.server.BasaltServer
import io.undertow.websockets.core.WebSocketChannel
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Type alias equivalent to an `Object2ObjectOpenHashMap<String, BasaltPlayer>`
 */

typealias PlayerMap = Object2ObjectOpenHashMap<String, BasaltPlayer>

/**
 * Context class which stores a reference to the [BasaltServer], a WebSocketChannel and the User ID associated
 * with that channel, as well as a [PlayerMap] and a Sequence Number of Sent, Successful Events.
 *
 * @property server A [BasaltServer] reference.
 * @property channel An Undertow WebSocketChannel.
 * @property userId The User ID associated with the WebSocketChannel (provided as a header).
 * @property players The internal [PlayerMap] which associates Guild ID's with [BasaltPlayer]s.
 * @property seq A sequence counter which stores the amount of events sent in response to successful requests.
 *
 * @author Sam Pritchard
 * @since 1.0
 * @constructor Constructs a SocketContext from a [BasaltServer], a WebSocketChannel and a User ID String.
 */

class SocketContext internal constructor(val server: BasaltServer, val channel: WebSocketChannel, val userId: String) {
    internal val players = PlayerMap()
    internal val seq = AtomicLong(0)
}