/*
Copyright 2018 Sam Pritchard

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
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
 * Context class which stores a reference to a [BasaltServer], a WebSocketChannel and the User ID associated
 * with that channel, as well as a PlayerMap that indexes [BasaltPlayers][BasaltPlayer] by Guild ID.
 *
 * @property server A [BasaltServer] reference.
 * @property channel An Undertow WebSocketChannel.
 * @property userId The User ID associated with the WebSocketChannel.
 *
 * @author Sam Pritchard
 * @since 1.0
 * @constructor Constructs a SocketContext from a [BasaltServer], a WebSocketChannel and a User ID String.
 */

class SocketContext internal constructor(val server: BasaltServer, val channel: WebSocketChannel, val userId: String) {
    /**
     * @suppress
     */
    internal val players = PlayerMap()
}