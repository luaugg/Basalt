package basalt.server

import basalt.exceptions.ConfigurationException
import basalt.exceptions.MissingContextException
import basalt.messages.client.*
import basalt.messages.server.DispatchResponse
import basalt.messages.server.JsonTrack
import basalt.messages.server.LoadTrackResponse
import basalt.player.AudioLoadHandler
import basalt.player.BasaltPlayer
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
import space.npstr.magma.MagmaMember
import space.npstr.magma.MagmaServerUpdate
import java.io.File
import java.util.*
import kotlin.math.ceil
import kotlin.math.min

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
    lateinit var playerManager: DefaultAudioPlayerManager
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

        playerManager = DefaultAudioPlayerManager()
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
        GlobalScope.launch {
            val data = JsonIterator.deserialize(msg)
            when (val name = data["op"].toString()) {
                "init", "initialize" -> {
                    val request = JsonIterator.deserialize(msg, InitializeRequest::class.java)
                    val socketContext = socketContextMap[userId] ?: throw MissingContextException(userId)
                    if (socketContext.players[request.guildId] != null) {
                        LOGGER.warn("Player already initialized for User ID: {} and Guild ID: {}", userId, request.guildId)
                        socket.error(request.guildId, ErrorResponses.PLAYER_ALREADY_INITIALIZED)
                        return@launch
                    }
                    val member = MagmaMember.builder()
                            .guildId(request.guildId)
                            .userId(userId)
                            .build()
                    val update = MagmaServerUpdate.builder()
                            .endpoint(request.endpoint)
                            .sessionId(request.sessionId)
                            .token(request.token)
                            .build()
                    val player = BasaltPlayer(vertx, socket, playerManager.createPlayer(), request.guildId)
                    socketContext.players[request.guildId] = player
                    magma.provideVoiceServerUpdate(member, update)
                    socket.send(MessageTypes.INITIALIZED, request.guildId, null)
                    LOGGER.info("Initialized player for User ID: {} and Guild ID: {}", userId, request.guildId)
                }
                "play", "start" -> {
                    val request = JsonIterator.deserialize(msg, PlayRequest::class.java)
                    val socketContext = socketContextMap[userId] ?: throw MissingContextException(userId)
                    val player = socketContext.players[request.guildId]
                    if (player == null) {
                        LOGGER.warn("Player not initialized for User ID: {} and Guild ID: {}", userId, request.guildId)
                        socket.error(request.guildId, ErrorResponses.PLAYER_NOT_INITIALIZED)
                        return@launch
                    }
                    val member = MagmaMember.builder()
                            .userId(userId)
                            .guildId(request.guildId)
                            .build()
                    if (player.audioSender == null)
                        player.audioSender = AudioSender(player)
                    magma.setSendHandler(member, player.audioSender)
                    val track = trackUtil.toAudioTrack(request.track)
                    if (request.startTime != null) {
                        if (request.startTime >= track.duration) {
                            LOGGER.warn("Position: {}ms out of bounds for User ID: {}, Guild ID: {}", userId, request.guildId)
                            socket.error(request.guildId, ErrorResponses.POSITION_OUT_OF_BOUNDS)
                            return@launch
                        }
                    }
                    player.player.playTrack(track)
                    LOGGER.debug("Playing Track: {} for User ID: {} and Guild ID: {}", userId, request.guildId)
                }
                "stop" -> {
                    val request = JsonIterator.deserialize(msg, EmptyRequestBody::class.java)
                    val socketContext = socketContextMap[userId] ?: throw MissingContextException(userId)
                    val player = socketContext.players[request.guildId]
                    if (player == null) {
                        LOGGER.warn("Player not initialized for Guild ID: {}", request.guildId)
                        socket.error(request.guildId, ErrorResponses.PLAYER_NOT_INITIALIZED)
                        return@launch
                    }
                    if (player.player.playingTrack == null) {
                        LOGGER.warn("Track not playing (upon attempting to stop) for User ID: {} and Guild ID: {}", userId, request.guildId)
                        socket.error(request.guildId, ErrorResponses.NO_TRACK)
                        return@launch
                    }
                    player.player.stopTrack()
                    LOGGER.debug("Stopped track for User ID: {} and Guild ID: {}", userId, request.guildId)
                }
                "destroy" -> {
                    val request = JsonIterator.deserialize(msg, EmptyRequestBody::class.java)
                    val socketContext = socketContextMap[userId] ?: throw MissingContextException(userId)
                    val player = socketContext.players[request.guildId]
                    if (player == null) {
                        LOGGER.warn("Player not initialized User ID: {} and Guild ID: {}", userId, request.guildId)
                        socket.error(request.guildId, ErrorResponses.PLAYER_NOT_INITIALIZED)
                        return@launch
                    }
                    player.audioSender = null
                    player.player.destroy()
                    socketContextMap.remove(userId)
                    LOGGER.info("Player for User ID: {} and Guild ID: {}", userId, request.guildId)
                    socket.send(MessageTypes.DESTROYED, request.guildId, null)
                }
                "pause" -> {
                    val request = JsonIterator.deserialize(msg, EmptyRequestBody::class.java)
                    val socketContext = socketContextMap[userId] ?: throw MissingContextException(userId)
                    val player = socketContext.players[request.guildId]
                    if (player == null) {
                        LOGGER.warn("Player not initialized User ID: {} and Guild ID: {}", userId, request.guildId)
                        socket.error(request.guildId, ErrorResponses.PLAYER_NOT_INITIALIZED)
                        return@launch
                    }
                    if (player.player.playingTrack == null) {
                        LOGGER.warn("Track not playing (upon attempting to pause) for User ID: {} and Guild ID: {}", userId, request.guildId)
                        socket.error(request.guildId, ErrorResponses.NO_TRACK)
                        return@launch
                    }
                    if (player.player.isPaused) {
                        LOGGER.warn("Player already paused for User ID: {} and Guild ID: {}", userId, request.guildId)
                        socket.error(request.guildId, ErrorResponses.PLAYER_ALREADY_PAUSED)
                        return@launch
                    }
                    player.player.isPaused = true
                    LOGGER.debug("Paused player for User ID: {} and Guild ID: {}", userId, request.guildId)
                }
                "resume" -> {
                    val request = JsonIterator.deserialize(msg, EmptyRequestBody::class.java)
                    val socketContext = socketContextMap[userId] ?: throw MissingContextException(userId)
                    val player = socketContext.players[request.guildId]
                    if (player == null) {
                        LOGGER.warn("Player not initialized User ID: {} and Guild ID: {}", userId, request.guildId)
                        socket.error(request.guildId, ErrorResponses.PLAYER_NOT_INITIALIZED)
                        return@launch
                    }
                    if (player.player.playingTrack == null) {
                        LOGGER.warn("Track not playing (upon attempting to resume) for User ID: {} and Guild ID: {}", userId, request.guildId)
                        socket.error(request.guildId, ErrorResponses.NO_TRACK)
                        return@launch
                    }
                    if (!player.player.isPaused) {
                        LOGGER.warn("Player already resumed for User ID: {} and Guild ID: {}", userId, request.guildId)
                        socket.error(request.guildId, ErrorResponses.PLAYER_ALREADY_RESUMED)
                        return@launch
                    }
                    player.player.isPaused = false
                    LOGGER.debug("Resumed player for User ID: {} and Guild ID: {}", userId, request.guildId)
                }
                "seek" -> {
                    val request = JsonIterator.deserialize(msg, SeekRequest::class.java)
                    val socketContext = socketContextMap[userId] ?: throw MissingContextException(userId)
                    val player = socketContext.players[request.guildId]
                    if (player == null) {
                        LOGGER.warn("Player not initialized User ID: {} and Guild ID: {}", userId, request.guildId)
                        socket.error(request.guildId, ErrorResponses.PLAYER_NOT_INITIALIZED)
                        return@launch
                    }
                    val playing = player.player.playingTrack
                    if (playing == null) {
                        LOGGER.warn("Track not playing (upon attempting to seek) for User ID: {} and Guild ID: {}", userId, request.guildId)
                        socket.error(request.guildId, ErrorResponses.NO_TRACK)
                        return@launch
                    }
                    if (!playing.isSeekable) {
                        LOGGER.warn("Track is not seekable (upon attempting to seek) for User ID: {} and Guild ID: {}", userId, request.guildId)
                        socket.error(request.guildId, ErrorResponses.TRACK_NOT_SEEKABLE)
                        return@launch
                    }
                    if (request.position >= playing.duration) {
                        LOGGER.warn("Position: {}ms out of bounds for User ID: {} and Guild ID: {}", userId, request.guildId)
                        socket.error(request.guildId, ErrorResponses.POSITION_OUT_OF_BOUNDS)
                        return@launch
                    }
                    playing.position = request.position
                    socket.send(MessageTypes.POSITION_UPDATE, request.guildId, request.position)
                    LOGGER.debug("Seeked to {}ms for User ID: {} and Guild ID: {}", request.position, userId, request.guildId)
                }
                "volume" -> {
                    val request = JsonIterator.deserialize(msg, SetVolumeRequest::class.java)
                    val socketContext = socketContextMap[userId] ?: throw MissingContextException(userId)
                    val player = socketContext.players[request.guildId]
                    if (player == null) {
                        LOGGER.warn("Player not initialized User ID: {} and Guild ID: {}", userId, request.guildId)
                        socket.error(request.guildId, ErrorResponses.PLAYER_NOT_INITIALIZED)
                        return@launch
                    }
                    if (request.volume < 0 || request.volume > 1000) {
                        LOGGER.warn("Volume out of bounds (0-1000) for User ID: {} and Guild ID: {}", userId, request.guildId)
                        socket.error(request.guildId, ErrorResponses.VOLUME_OUT_OF_BOUNDS)
                        return@launch
                    }
                    player.player.volume = request.volume
                    socket.send(MessageTypes.VOLUME_UPDATE, request.guildId, request.volume)
                    LOGGER.debug("Set volume to {} for User ID: {} and Guild ID: {}", request.volume, userId, request.guildId)
                }
                "loadIdentifiers" -> {
                    val request = JsonIterator.deserialize(msg, LoadRequest::class.java)
                    val identifiers = request.identifiers.toMutableList()
                    val iterator = identifiers.listIterator()
                    val chunks = ceil((request.identifiers.size.toDouble() / loadChunkSize.toDouble())).toInt()
                    for (i in 0 until chunks) {
                        val minimum = min(chunks, request.identifiers.size)
                        val array = arrayOfNulls<LoadTrackResponse>(minimum)
                        for (index in 1..minimum) {
                            val identifier = iterator.next()
                            iterator.remove()
                            AudioLoadHandler(this@BasaltServer).load(identifier)
                                    .thenApply { LoadTrackResponse(it) }
                                    .thenAccept { array[i] = it }
                                    .thenAccept {
                                        if (index == minimum)
                                            socket.send(MessageTypes.LOAD_IDENTIFIERS_CHUNK, null, array, request.key)
                                        if (i + 1 == chunks) {
                                            LOGGER.debug("Finished loading {} identifiers for User ID: {}", identifiers.size, userId)
                                            socket.send(MessageTypes.CHUNKS_FINISHED, null, null, request.key)
                                        }
                                    }
                        }
                    }
                }
            }
        }
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

fun WebSocketBase.send(type: MessageType, guildId: String?, data: Any?, key: String? = null) = writeTextMessage(JsonStream.serialize(DispatchResponse(guildId, type.type, data, key)))
fun WebSocketBase.error(guildId: String?, error: String) = send(MessageTypes.ERROR, guildId, error)
fun WebSocketBase.error(guildId: String?, error: ErrorResponse) = send(MessageTypes.ERROR, guildId, error.name)