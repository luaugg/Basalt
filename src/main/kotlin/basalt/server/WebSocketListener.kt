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
                    if (context.players[init.guildId] != null) {
                        LOGGER.error("Player already initialized for User ID: {} and Guild ID: {}", context.userId, init.guildId)
                        return
                    }
                    context.seq.incrementAndGet()
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
                    val player = server.contexts[channel]?.players?.get(play.guildId)
                    if (player == null) {
                        LOGGER.error("Player or SocketContext is null for Guild ID: {} (Try initializing first!)", play.guildId)
                        return
                    }
                    player.context.seq.incrementAndGet()
                    player.audioPlayer.playTrack(server.trackUtil.toAudioTrack(play.track))
                }
                "pause" -> {
                    val pause = JsonIterator.deserialize(message.data, SetPausedRequest::class.java)
                    val player = server.contexts[channel]?.players?.get(pause.guildId)
                    if (player == null) {
                        LOGGER.error("Player or SocketContext is null for Guild ID: {} (Try initializing first!)", pause.guildId)
                        return
                    }
                    player.context.seq.incrementAndGet()
                    player.audioPlayer.isPaused = pause.paused
                }
                "stop" -> {
                    val stop = JsonIterator.deserialize(message.data, StopRequest::class.java)
                    val player = server.contexts[channel]?.players?.get(stop.guildId)
                    if (player == null) {
                        LOGGER.error("Player or SocketContext is null for Guild ID: {} (Try initializing first!)", stop.guildId)
                        return
                    }
                    player.context.seq.incrementAndGet()
                    player.audioPlayer.stopTrack()
                }
                "destroy" -> {
                    val destroy = JsonIterator.deserialize(message.data, DestroyRequest::class.java)
                    val player = server.contexts[channel]?.players?.get(destroy.guildId)
                    if (player == null) {
                        LOGGER.error("Player or SocketContext is null for Guild ID: {} (Try initializing first!)", destroy.guildId)
                        return
                    }
                    player.context.seq.incrementAndGet()
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
                    val player = server.contexts[channel]?.players?.get(volume.guildId)
                    if (player == null) {
                        LOGGER.error("Player or SocketContext is null for Guild ID: {} (Try initializing first!)", volume.guildId)
                        return
                    }
                    if (volume.volume < 0 || volume.volume > 1000) {
                        LOGGER.warn("Volume cannot be negative or above 1000 for User ID: {} and Guild ID: {}",
                                player.context.userId, volume.guildId)
                        return
                    }
                    player.context.seq.incrementAndGet()
                    player.audioPlayer.volume = volume.volume
                    val response = DispatchResponse(player.context, volume.guildId, "VOLUME_UPDATE", volume.volume)
                    WebSockets.sendText(JsonStream.serialize(response), channel, null)
                }
                "seek" -> {
                    val seek = JsonIterator.deserialize(message.data, SeekRequest::class.java)
                    val player = server.contexts[channel]?.players?.get(seek.guildId)
                    if (player == null) {
                        LOGGER.error("Player or SocketContext is null for Guild ID: {} (Try initializing first!)", seek.guildId)
                        return
                    }
                    val track = player.audioPlayer.playingTrack
                    if (track == null) {
                        LOGGER.error("Track is null (attempt to seek through a non-existent track). User ID: {}, Guild ID: {}",
                                player.context.userId, seek.guildId)
                        return
                    }
                    if (!track.isSeekable) {
                        LOGGER.error("Track cannot be seeked through. User ID: {}, Guild ID: {}", player.context.userId, seek.guildId)
                        return
                    }
                    if (seek.position < 0 || seek.position > track.duration) {
                        LOGGER.error("Seek position cannot be negative or larger than the duration of the track. User ID: {}, Guild ID: {}",
                                player.context.userId, seek.guildId)
                        return
                    }
                    player.context.seq.incrementAndGet()
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