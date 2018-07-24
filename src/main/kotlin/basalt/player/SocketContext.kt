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
 * @author Sam Pritchard
 * @version 1.0
 * @constructor Constructs a SocketContext from a [BasaltServer], a WebSocketChannel and a User ID String.
 */

class SocketContext internal constructor(val server: BasaltServer, val channel: WebSocketChannel, val userId: String) {
    /**
     * The internal [PlayerMap] which associates Guild ID's with [BasaltPlayer]s.
     */
    internal val players = PlayerMap()
    /**
     * A sequence counter which stores the amount of events sent in response to successful events.
     *
     * This value is used mainly to synchronize requests and responses (between client and server).
     */
    internal val seq = AtomicLong(0)
}