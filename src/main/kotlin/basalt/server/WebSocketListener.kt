package basalt.server

import basalt.messages.client.InitializeRequest
import basalt.messages.client.LoadRequest
import basalt.messages.client.PlayRequest
import basalt.messages.server.DispatchResponse
import basalt.messages.server.LoadTrackResponse
import basalt.messages.server.TrackPair
import basalt.player.AudioLoadHandler
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
        server.players.remove(webSocketChannel)
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
                    val member = MagmaMember.builder()
                            .guildId(init.guildId)
                            .userId(server.players[channel]!!.userId)
                            .build()
                    val update = MagmaServerUpdate.builder()
                            .sessionId(init.sessionid)
                            .endpoint(init.endpoint)
                            .token(init.token)
                            .build()
                    server.magma.provideVoiceServerUpdate(member, update)
                    server.magma.setSendHandler(member, AudioSender(server.sourceManager.createPlayer()))
                    val response = DispatchResponse(name = "INITIALIZED", data = null)
                    WebSockets.sendText(JsonStream.serialize(response), channel, null)
                    LOGGER.info("Initialized connection from User ID: {} and Guild ID: {}", member.userId, member.guildId)
                    
                }
                "play" -> {
                    val play = JsonIterator.deserialize(message.data, PlayRequest::class.java)
                    val member = MagmaMember.builder()
                            .guildId(play.guildId)
                            //.userId(server.magma.)
                }
                "loadIdentifiers" -> {
                    val load = JsonIterator.deserialize(message.data, LoadRequest::class.java)
                    val identifiers = load.identifiers
                    val list = ObjectArrayList<LoadTrackResponse>(identifiers.size)
                    for (str in identifiers) {
                        AudioLoadHandler(server).load(str)
                                .thenApply { LoadTrackResponse(it) }
                                .thenAccept { list.add(it) }
                                .thenAccept {
                                    if (list.size == identifiers.size) {
                                        val response = DispatchResponse(name = "IDENTIFY_RESPONSE", data = list.toArray())
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