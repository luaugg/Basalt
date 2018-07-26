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
package basalt.server

import basalt.messages.client.*
import basalt.messages.server.DispatchResponse
import basalt.messages.server.LoadTrackResponse
import basalt.messages.server.PlayerUpdate
import basalt.player.AudioLoadHandler
import basalt.player.BasaltPlayer
import com.jsoniter.JsonIterator
import com.jsoniter.output.JsonStream
import io.undertow.websockets.core.*
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.slf4j.LoggerFactory
import space.npstr.magma.MagmaMember
import space.npstr.magma.MagmaServerUpdate

import basalt.server.ErrorResponses.*

/**
 * The listener class which responds to WebSocket Events, including (but not limited to) incoming messages.
 *
 * @property server A [BasaltServer] reference used mainly to access information.
 *
 * @author Sam Pritchard
 * @since 1.0
 * @constructor Constructs a WebSocketListener from a [BasaltServer] instance.
 */

class WebSocketListener internal constructor(private val server: BasaltServer): AbstractReceiveListener() {
    /**
     * Fired by Undertow when the WebSocket Connection between Basalt and a client closes.
     * @param webSocketChannel The WebSocketChannel that was closed.
     * @param channel The StreamSourceFrameChannel that was closed.
     */
    override fun onClose(webSocketChannel: WebSocketChannel, channel: StreamSourceFrameChannel) {
        super.onClose(webSocketChannel, channel)
        server.contexts.remove(webSocketChannel)
        if (webSocketChannel.isCloseInitiatedByRemotePeer) {
            val host = webSocketChannel.sourceAddress
                    .toString()
                    .replaceFirst("/", "")
                    .replaceFirst("0:0:0:0:0:0:0:1", "localhost")
            LOGGER.info("Connection closed from {}", host)
        }
    }

    /**
     * Fired by Undertow upon an error that was thrown during WebSocket Communication.
     * @param channel The WebSocketChannel that errored.
     * @param error The Throwable instance that was thrown.
     */
    override fun onError(channel: WebSocketChannel, error: Throwable) {
        super.onError(channel, error)
        LOGGER.error("Error during WebSocket Communication!", error)
    }

    /**
     * Fired by Undertow upon a full text message (such as JSON Data) being sent by a client.
     * @param channel The WebSocketChannel that data was sent to.
     * @param message The BufferedTextMessage data that was sent.
     */
    override fun onFullTextMessage(channel: WebSocketChannel, message: BufferedTextMessage) {
        try {
            val data = JsonIterator.deserialize(message.data)
            when (data["op"]!!.toString()) {
                "initialize" -> {
                    val init = JsonIterator.deserialize(message.data, InitializeRequest::class.java)
                    val context = server.contexts[channel]
                    if (context == null) {
                        LOGGER.error("SocketContext is null for Guild ID: {}\nThis should never happen!", init.guildId)
                        return
                    }
                    context.seq.incrementAndGet()
                    if (context.players[init.guildId] != null) {
                        LOGGER.warn("Player already initialized for User ID: {} and Guild ID: {}", context.userId, init.guildId)
                        val response = DispatchResponse(context, init.guildId, "ERROR", PLAYER_ALREADY_INITIALIZED.name)
                        WebSockets.sendText(JsonStream.serialize(response), channel, null)
                        return
                    }
                    val member = MagmaMember.builder()
                            .guildId(init.guildId)
                            .userId(context.userId)
                            .build()
                    val update = MagmaServerUpdate.builder()
                            .sessionId(init.sessionid)
                            .endpoint(init.endpoint)
                            .token(init.token)
                            .build()
                    val basalt = BasaltPlayer(context, init.guildId, server.sourceManager.createPlayer())
                    context.players[init.guildId] = basalt
                    server.magma.provideVoiceServerUpdate(member, update)
                    server.magma.setSendHandler(member, AudioSender(basalt.audioPlayer))
                    val response = DispatchResponse(context, init.guildId, "INITIALIZED")
                    WebSockets.sendText(JsonStream.serialize(response), channel, null)
                    LOGGER.info("Initialized connection from User ID: {} and Guild ID: {}", member.userId, member.guildId)
                }
                "play" -> {
                    val play = JsonIterator.deserialize(message.data, PlayRequest::class.java)
                    val context = server.contexts[channel]
                    val guildId = play.guildId
                    if (context == null) {
                        LOGGER.error("SocketContext is null for this WebSocketChannel (Guild ID: {})!", guildId)
                        return
                    }
                    context.seq.incrementAndGet()
                    val player = context.players[guildId]
                    if (player == null) {
                        LOGGER.warn("Player is null for Guild ID: {} and User ID: {} (Try initializing first!)", guildId, context.userId)
                        val response = DispatchResponse(context, guildId, "ERROR", PLAYER_NOT_INITIALIZED.name)
                        WebSockets.sendText(JsonStream.serialize(response), channel, null)
                        return
                    }
                    player.audioPlayer.playTrack(server.trackUtil.toAudioTrack(play.track))
                }
                "pause" -> {
                    val pause = JsonIterator.deserialize(message.data, SetPausedRequest::class.java)
                    val context = server.contexts[channel]
                    val guildId = pause.guildId
                    if (context == null) {
                        LOGGER.error("SocketContext is null for this WebSocketChannel (Guild ID: {})!", guildId)
                        return
                    }
                    context.seq.incrementAndGet()
                    val player = context.players[guildId]
                    if (player == null) {
                        LOGGER.warn("Player is null for Guild ID: {} and User ID: {} (Try initializing first!)", guildId, context.userId)
                        val response = DispatchResponse(context, guildId, "ERROR", PLAYER_NOT_INITIALIZED.name)
                        WebSockets.sendText(JsonStream.serialize(response), channel, null)
                        return
                    }
                    val paused = pause.paused
                    if (player.audioPlayer.isPaused == paused) {
                        val enum: String?
                        val msg: String?
                        if (paused) {
                            enum = PLAYER_ALREADY_PAUSED.name
                            msg = "Player is already paused for"
                        }
                        else {
                            enum = PLAYER_ALREADY_RESUMED.name
                            msg = "Player has already been resumed for"
                        }
                        LOGGER.warn("{} Guild ID: {} and User ID: {}", msg, guildId, context.userId)
                        val response = DispatchResponse(context, guildId, "ERROR", enum)
                        WebSockets.sendText(JsonStream.serialize(response), channel, null)
                        return
                    }
                    player.audioPlayer.isPaused = pause.paused
                }
                "stop" -> {
                    val stop = JsonIterator.deserialize(message.data, StopRequest::class.java)
                    val context = server.contexts[channel]
                    val guildId = stop.guildId
                    if (context == null) {
                        LOGGER.error("SocketContext is null for this WebSocketChannel (Guild ID: {})!", guildId)
                        return
                    }
                    context.seq.incrementAndGet()
                    val player = context.players[guildId]
                    if (player == null) {
                        LOGGER.warn("Player is null for Guild ID: {} and User ID: {} (Try initializing first!)", guildId, context.userId)
                        val response = DispatchResponse(context, guildId, "ERROR", PLAYER_NOT_INITIALIZED.name)
                        WebSockets.sendText(JsonStream.serialize(response), channel, null)
                        return
                    }
                    if (player.audioPlayer.playingTrack == null) {
                        LOGGER.warn("Track is null for Guild ID: {} and User ID: {}", guildId, context.userId)
                        val response = DispatchResponse(context, guildId, "ERROR", NO_TRACK.name)
                        WebSockets.sendText(JsonStream.serialize(response), channel, null)
                        return
                    }
                    player.audioPlayer.stopTrack()
                }
                "destroy" -> {
                    val destroy = JsonIterator.deserialize(message.data, DestroyRequest::class.java)
                    val context = server.contexts[channel]
                    val guildId = destroy.guildId
                    if (context == null) {
                        LOGGER.error("SocketContext is null for this WebSocketChannel (Guild ID: {})!", guildId)
                        return
                    }
                    context.seq.incrementAndGet()
                    val player = context.players[guildId]
                    if (player == null) {
                        LOGGER.warn("Player is null for Guild ID: {} and User ID: {} (Try initializing first!)", guildId, context.userId)
                        val response = DispatchResponse(context, guildId, "ERROR", PLAYER_NOT_INITIALIZED.name)
                        WebSockets.sendText(JsonStream.serialize(response), channel, null)
                        return
                    }
                    player.audioPlayer.destroy()
                    player.context.players.remove(destroy.guildId)
                    val member = MagmaMember.builder()
                            .guildId(destroy.guildId)
                            .userId(player.context.userId)
                            .build()
                    server.magma.removeSendHandler(member)
                    server.magma.closeConnection(member)
                    val response = DispatchResponse(player.context, destroy.guildId, "DESTROYED")
                    WebSockets.sendText(JsonStream.serialize(response), channel, null)
                }
                "volume" -> {
                    val volume = JsonIterator.deserialize(message.data, SetVolumeRequest::class.java)
                    val context = server.contexts[channel]
                    val guildId = volume.guildId
                    if (context == null) {
                        LOGGER.error("SocketContext is null for this WebSocketChannel (Guild ID: {})!", guildId)
                        return
                    }
                    context.seq.incrementAndGet()
                    val player = context.players[guildId]
                    if (player == null) {
                        LOGGER.warn("Player is null for Guild ID: {} and User ID: {} (Try initializing first!)", guildId, context.userId)
                        val response = DispatchResponse(context, guildId, "ERROR", PLAYER_NOT_INITIALIZED.name)
                        WebSockets.sendText(JsonStream.serialize(response), channel, null)
                        return
                    }
                    if (volume.volume < 0 || volume.volume > 1000) {
                        LOGGER.warn("Volume cannot be negative or above 1000 for User ID: {} and Guild ID: {}",
                                player.context.userId, volume.guildId)
                        val response = DispatchResponse(context, guildId, "ERROR", VOLUME_OUT_OF_BOUNDS.name)
                        WebSockets.sendText(JsonStream.serialize(response), channel, null)
                        return
                    }
                    player.audioPlayer.volume = volume.volume
                    val response = DispatchResponse(player.context, volume.guildId, "VOLUME_UPDATE", volume.volume)
                    WebSockets.sendText(JsonStream.serialize(response), channel, null)
                }
                "seek" -> {
                    val seek = JsonIterator.deserialize(message.data, SeekRequest::class.java)
                    val context = server.contexts[channel]
                    val guildId = seek.guildId
                    if (context == null) {
                        LOGGER.error("SocketContext is null for this WebSocketChannel (Guild ID: {})!", guildId)
                        return
                    }
                    context.seq.incrementAndGet()
                    val player = context.players[guildId]
                    if (player == null) {
                        LOGGER.warn("Player is null for Guild ID: {} and User ID: {} (Try initializing first!)", guildId, context.userId)
                        val response = DispatchResponse(context, guildId, "ERROR", PLAYER_NOT_INITIALIZED.name)
                        WebSockets.sendText(JsonStream.serialize(response), channel, null)
                        return
                    }
                    val track = player.audioPlayer.playingTrack
                    if (track == null) {
                        LOGGER.warn("Track is null for Guild ID: {} and User ID: {}", seek.guildId, context.userId)
                        val response = DispatchResponse(context, guildId, "ERROR", NO_TRACK.name)
                        WebSockets.sendText(JsonStream.serialize(response), channel, null)
                        return
                    }
                    if (!track.isSeekable) {
                        LOGGER.warn("Track is not seekable for Guild ID: {} and User ID: {}", seek.guildId, context.userId)
                        val response = DispatchResponse(context, guildId, "ERROR", TRACK_NOT_SEEKABLE.name)
                        WebSockets.sendText(JsonStream.serialize(response), channel, null)
                        return
                    }
                    if (seek.position < 0 || seek.position > track.duration) {
                        LOGGER.warn("Seek position cannot be negative or larger than the duration of the track! Guild ID: {} and User ID: {}",
                                seek.guildId, context.userId)
                        val response = DispatchResponse(context, guildId, "ERROR", POSITION_OUT_OF_BOUNDS.name)
                        WebSockets.sendText(JsonStream.serialize(response), channel, null)
                        return
                    }
                    player.audioPlayer.playingTrack.position = seek.position
                    val response = PlayerUpdate(seek.guildId, seek.position, System.currentTimeMillis())
                    WebSockets.sendText(JsonStream.serialize(response), channel, null)
                }
                "load" -> {
                    val load = JsonIterator.deserialize(message.data, LoadRequest::class.java)
                    val context = server.contexts[channel]
                    if (context == null) {
                        LOGGER.error("SocketContext is null. This should *never* happen.")
                        return
                    }
                    context.seq.incrementAndGet()
                    val identifiers = load.identifiers
                    val list = ObjectArrayList<LoadTrackResponse>(identifiers.size)
                    for (str in identifiers) {
                        AudioLoadHandler(server).load(str)
                                .thenApply { LoadTrackResponse(it) }
                                .thenAccept { list.add(it) }
                                .thenAccept {
                                    if (list.size == identifiers.size) {
                                        val response = DispatchResponse(context, name = "IDENTIFY_RESPONSE", data = list.toArray())
                                        WebSockets.sendText(JsonStream.serialize(response), channel, null)
                                    }
                                }
                    }
                }
            }
        } catch (err: Throwable) {
            LOGGER.error("Error when responding to text message!", err)
        }
    }

    /**
     * @suppress
     */
    companion object {
        /**
         * @suppress
         */
        private val LOGGER = LoggerFactory.getLogger(WebSocketListener::class.java)
    }
}