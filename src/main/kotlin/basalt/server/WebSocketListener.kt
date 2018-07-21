package basalt.server

import com.jsoniter.JsonIterator
import io.undertow.websockets.core.*
import org.slf4j.LoggerFactory

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
                "" -> {}
            }
        } catch (err: Throwable) {
            LOGGER.error("Error when responding to text message! {}", message.data)
        }
    }
    companion object {
        private val LOGGER = LoggerFactory.getLogger(WebSocketListener::class.java)
    }
}