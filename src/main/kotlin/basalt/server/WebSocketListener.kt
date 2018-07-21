package basalt.server

import basalt.messages.client.InitializeEvent
import com.jsoniter.JsonIterator
import io.undertow.websockets.core.*
import org.slf4j.LoggerFactory
import space.npstr.magma.MagmaMember
import space.npstr.magma.MagmaServerUpdate

class WebSocketListener(val server: BasaltServer): AbstractReceiveListener() {
    override fun onClose(webSocketChannel: WebSocketChannel, channel: StreamSourceFrameChannel) {
        super.onClose(webSocketChannel, channel)
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
                    val init = JsonIterator.deserialize(message.data, InitializeEvent::class.java)
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