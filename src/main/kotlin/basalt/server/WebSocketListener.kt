package basalt.server

import basalt.messages.client.*
import basalt.messages.server.DispatchResponse
import basalt.messages.server.LoadTrackResponse
import basalt.messages.server.TrackPair
import basalt.player.AudioLoadHandler
import basalt.player.BasaltPlayer
import com.jsoniter.JsonIterator
import com.jsoniter.output.JsonStream
import io.undertow.websockets.core.*
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.slf4j.LoggerFactory
import space.npstr.magma.MagmaMember
import space.npstr.magma.MagmaServerUpdate

class WebSocketListener(val server: BasaltServer): AbstractReceiveListener() {
    override fun onClose(webSocketChannel: WebSocketChannel, channel: StreamSourceFrameChannel) {
        super.onClose(webSocketChannel, channel)
        server.contexts.remove(webSocketChannel)
        val host = webSocketChannel.sourceAddress
                .toString()
                .replaceFirst("/", "")
                .replaceFirst("0:0:0:0:0:0:0:1", "localhost")
        LOGGER.info("Connection closed from {}", host)
    }
    override fun onError(channel: WebSocketChannel, error: Throwable) {
        super.onError(channel, error)
        LOGGER.error("Error during WebSocket Communication!", error)
    }
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
                "volume" -> {
                    val volume = JsonIterator.deserialize(message.data, SetVolumeRequest::class.java)
                    val player = server.contexts[channel]?.players?.get(volume.guildId)
                    if (player == null) {
                        LOGGER.error("Player or SocketContext is null for Guild ID: {} (Try initializing first)!", volume.guildId)
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
                "loadIdentifiers" -> {
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
    companion object {
        private val LOGGER = LoggerFactory.getLogger(WebSocketListener::class.java)
    }
}