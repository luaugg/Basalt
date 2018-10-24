package basalt.server

import basalt.exceptions.ConfigurationException
import basalt.messages.client.InitializeRequest
import basalt.messages.server.JsonTrack
import basalt.messages.server.LoadTrackResponse
import basalt.player.AudioLoadHandler
import basalt.player.SocketContext
import basalt.util.AudioTrackUtil
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.github.shredder121.asyncaudio.jda.AsyncPacketProviderFactory
import com.jsoniter.JsonIterator
import com.jsoniter.output.JsonStream
import com.sedmelluq.discord.lavaplayer.jdaudp.NativeAudioSendFactory
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.beam.BeamAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.http.WebSocketBase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import space.npstr.magma.MagmaApi
import java.io.File

/*
basalt:
  server:
    socket:
      address: localhost
      port: 8017
    http:
      address: localhost
      port: 4591
    options:
      password:
      sessionExpirationSeconds: 120
      statsIntervalSeconds: 30
      bufferDurationMs: 400
      httpEnabled: true
      loggingLevel: INFO
      sentryDsn:
      youtubePageLimit: 10
      loadChunkSize: 25
  players:
    sources:
      youtube: true
      soundcloud: true
      bandcamp: true
      vimeo: true
      twitch: true
      mixer: true
      http: true
      local: false
 */

class BasaltServer: AbstractVerticle() {
    lateinit var password: String
    lateinit var magma: MagmaApi
    lateinit var trackUtil: AudioTrackUtil
    lateinit var socketContextMap: HashMap<String, SocketContext>
    var loadChunkSize = 25 // lateinit doesn't work on primitives unfortunately

    override fun start(startFuture: Future<Void>) {
        val mapper = ObjectMapper(YAMLFactory())
        val config = mapper.readTree(File("basalt.yml")).getNotNull("basalt")
        val serverConfig = config.getNotNull("server")
        val socket = serverConfig.getNotNull("socket")
        val options = serverConfig.getNotNull("options")

        val playerConfig = config.getNotNull("players")
        val sources = playerConfig.getNotNull("sources")

        val socketAddress = socket.getNotNull("address")
        val socketPort = socket.getNotNull("port")
        // note to self: check if http is enabled then validate

        val playerManager = DefaultAudioPlayerManager()
        if (sources.getNotNull("youtube").booleanValue()) {
            val limit = options.getNotNull("youtubePageLimit").asInt()
            val youtubeManager = YoutubeAudioSourceManager(true)
            youtubeManager.setPlaylistPageCount(limit)
            playerManager.registerSourceManager(youtubeManager)
            LOGGER.info("YouTube Audio Source Manager registered!")
        }

        if (sources.getNotNull("soundcloud").booleanValue()) {
            playerManager.registerSourceManager(SoundCloudAudioSourceManager(true))
            LOGGER.info("SoundCloud Audio Source Manager registered!")
        }
        if (sources.getNotNull("bandcamp").booleanValue()) {
            playerManager.registerSourceManager(BandcampAudioSourceManager())
            LOGGER.info("Bandcamp Audio Source Manager registered!")
        }
        if (sources.getNotNull("vimeo").booleanValue()) {
            playerManager.registerSourceManager(VimeoAudioSourceManager())
            LOGGER.info("Vimeo Audio Source Manager registered!")
        }
        if (sources.getNotNull("twitch").booleanValue()) {
            playerManager.registerSourceManager(TwitchStreamAudioSourceManager())
            LOGGER.info("Twitch Audio Source Manager registered!")
        }
        if (sources.getNotNull("mixer").booleanValue()) {
            playerManager.registerSourceManager(BeamAudioSourceManager())
            LOGGER.info("Beam/Mixer Audio Source Manager registered!")
        }
        if (sources.getNotNull("http").booleanValue()) {
            playerManager.registerSourceManager(HttpAudioSourceManager())
            LOGGER.info("HTTP Audio Source Manager registered!")
        }
        if (sources.getNotNull("local").booleanValue()) {
            playerManager.registerSourceManager(LocalAudioSourceManager())
            LOGGER.info("Local Audio Source Manager registered!")
        }

        magma = MagmaApi.of { AsyncPacketProviderFactory.adapt(NativeAudioSendFactory(options.getNotNull("bufferDurationMs").intValue())) }
        loadChunkSize = options.getNotNull("loadChunkSize").intValue()
        password = options.getNotNull("password").textValue()
        trackUtil = AudioTrackUtil(this)
        socketContextMap = HashMap()

        /* -- HTTP Connection -- */

        if (options.getNotNull("httpEnabled").booleanValue()) {
            val httpServer = vertx.createHttpServer()
            httpServer.requestHandler { request ->
                val auth = request.getHeader("Authorization") ?: ""
                val response = request.response()
                if (auth != password) {
                    LOGGER.warn("Invalid password!")
                    response.putHeader("WWW-Authenticate", "None realm=\"Identifier loading.\"")
                    response.putHeader("Content-Type", "text/plain")
                    response.statusCode = 401
                    response.end("Unauthorized -- Invalid Status Code")
                    return@requestHandler
                }

                when (val path = request.path()) {
                    "loadidentifiers" -> {
                        GlobalScope.launch {
                            val params = request.params()
                            val identifiers = params?.getAll("identifiers")
                            if (params == null || params.isEmpty || identifiers == null || identifiers.isEmpty()) {
                                LOGGER.warn("No identifiers supplied!")
                                response.statusCode = 400
                                response.putHeader("Content-Type", "text/plan")
                                response.end("Bad Request -- Missing identifiers query parameter")
                                return@launch
                            }

                            val rawResponse = arrayOfNulls<LoadTrackResponse>(identifiers.size)
                            identifiers.forEachIndexed { index, identifier ->
                                AudioLoadHandler(this@BasaltServer).load(identifier)
                                        .thenApply { LoadTrackResponse(it) }
                                        .thenAccept { rawResponse[index] = it }
                                        .thenAccept {
                                            if (index + 1 == identifiers.size) {
                                                response.putHeader("Content-Type", "application/json")
                                                response.end(JsonStream.serialize(rawResponse))
                                            }
                                        }
                            }
                        }
                    }
                    "decodetracks" -> {
                        GlobalScope.launch {
                            val params = request.params()
                            val tracks = params?.getAll("tracks")
                            if (params == null || params.isEmpty || tracks == null || tracks.isEmpty()) {
                                LOGGER.warn("No tracks supplied!")
                                response.statusCode = 400
                                response.putHeader("Content-Type", "text/plain")
                                response.end("Bad Request -- Missing tracks query parameter")
                                return@launch
                            }

                            try {
                                val rawResponse = arrayOfNulls<JsonTrack>(tracks.size)
                                tracks.forEachIndexed { index, track ->
                                    rawResponse[index] = JsonTrack(trackUtil.toAudioTrack(track))
                                }
                                response.putHeader("Content-Type", "application/json")
                                response.end(JsonStream.serialize(rawResponse))
                            } catch (err: Throwable) {
                                LOGGER.error("Error when decoding tracks!", err)
                                response.statusCode = 500
                                response.putHeader("Content-Type", "text/plain")
                                response.end("Internal Server Error")
                            }
                        }
                    }
                    else -> {
                        LOGGER.warn("Unhandled path: {}", path)
                    }
                }
            }
            val http = serverConfig.getNotNull("http")
            httpServer.listen(http.getNotNull("port").intValue(), http.getNotNull("address").textValue())
        }

        /* -- WebSocket Connection -- */

        val webSocketServer = vertx.createHttpServer()
        webSocketServer.websocketHandler { ws ->
            val headers = ws.headers()
            val auth = headers["Authorization"] ?: ""
            if (auth != password) {
                LOGGER.warn("Invalid Authorization header!")
                ws.reject(4001)
                return@websocketHandler
            }

            val userId = headers["User-Id"]
            if (userId == null) {
                LOGGER.warn("Missing User-Id header!")
                ws.reject(4002)
                return@websocketHandler
            }

            val context = socketContextMap[userId]
            if (context != null) {
                context.webSocket.close(1001, "Socket resumed connection!")
                context.webSocket = ws
            } else {
                socketContextMap[userId] = SocketContext(userId, ws)
            }
            ws.textMessageHandler { handleTextMessage(ws, userId, it) }
            ws.accept()
        }
        startFuture.complete()
    }

    override fun stop(stopFuture: Future<Void>) {

    }

    private fun handleTextMessage(socket: WebSocketBase, userId: String, msg: String) {

    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(BasaltServer::class.java)
    }
}


fun JsonNode.getNotNull(name: String): JsonNode {
    val node = get(name)
    if (node == null || node.nodeType === JsonNodeType.NULL || node.nodeType === JsonNodeType.MISSING)
        throw ConfigurationException(name)
    return node
}