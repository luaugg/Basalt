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

import io.vertx.core.http.ServerWebSocket

/**
 * A SocketContext meant to associate a User ID with a WebSocket connection and a map of players as well as the ID
 * of a timer to be scheduled for resume purposes when the connection closes.
 *
 * @author Sam Pritchard
 * @since 4.0.0
 *
 * @property webSocket The actual raw WebSocket connection between a client and Basalt.
 * @property players A map containing [players][BasaltPlayer] for guilds.
 * @property resumeTimer The possibly-null ID of a Vert.x timer used for connection resuming.
 */

class SocketContext(var webSocket: ServerWebSocket) {
    val players = HashMap<String, BasaltPlayer>()
    var resumeTimer: Long? = null
}