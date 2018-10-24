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

import basalt.exceptions.ConfigurationException
import basalt.exceptions.MissingContextException
import basalt.messages.client.*
import basalt.messages.server.DispatchResponse
import basalt.messages.server.JsonTrack
import basalt.messages.server.LoadTrackResponse
import basalt.messages.server.StatsUpdate
import basalt.player.AudioLoadHandler
import basalt.player.BasaltPlayer
import basalt.player.SocketContext
import basalt.util.AudioTrackUtil
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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
import io.sentry.Sentry
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.http.HttpServer
import io.vertx.core.http.WebSocketBase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import space.npstr.magma.MagmaApi
import space.npstr.magma.MagmaMember
import space.npstr.magma.MagmaServerUpdate
import java.io.File
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.ceil
import kotlin.math.min

/**
 * The main entry class for Basalt which is responsible for every single last part of the application.
 * As of v4, this class handles most things, such as HTTP and WebSocket connections and timers, itself without shipping
 * those tasks off to other classes.
 *
 * @author Sam Pritchard
 * @since 4.0.0
 *
 * @property password The password that every connection made to Basalt must supply. Case-sensitive.
 * @property magma The Magma API class responsible for the actual audio part of Basalt.
 * @property trackUtil The [AudioTrackUtil] class responsible for encoding and decoding tracks.
 * @property socketContextMap The map containing all the connected sessions (including ones to be resumed).
 * @property playerManager The player manager which loads sources and is used to create the player.
 * @property httpServer The HTTP Server which can be turned on/off in the configuration file.
 * @property webSocketServer The WebSocket Server which is enabled by default and is the main way to communicate with Basalt.
 * @property sessionExpirationSeconds The amount of seconds to wait before destroying a session associated with a User ID.
 * @property loadChunkSize The amount of identifiers to fit in a single load chunk.
 * @property statsTimerId The ID of the Vert.x timer used to schedule stats updates.
 */

class BasaltServer: AbstractVerticle() {
    lateinit var password: String
    lateinit var magma: MagmaApi
    lateinit var trackUtil: AudioTrackUtil
    lateinit var socketContextMap: HashMap<String, SocketContext>
    lateinit var playerManager: DefaultAudioPlayerManager
    lateinit var httpServer: HttpServer
    lateinit var webSocketServer: HttpServer

    var sessionExpirationSeconds = -1
    var loadChunkSize = -1 // lateinit doesn't work on primitives unfortunately
    var statsTimerId: Long? = null

    /**
     * @suppress
     */
    override fun start(startFuture: Future<Void>) {
        val mapper = ObjectMapper(YAMLFactory())
        val config = mapper.readTree(File("basalt.yml")).getNotNull("basalt")
        val serverConfig = config.getNotNull("server")
        val socket = serverConfig.getNotNull("socket")
        val options = serverConfig.getNotNull("options")

        val playerConfig = config.getNotNull("players")
        val sources = playerConfig.getNotNull("sources")

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
            httpServer = vertx.createHttpServer()
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

        webSocketServer = vertx.createHttpServer()
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
                context.resumeTimer?.let { vertx.cancelTimer(it) }
                context.webSocket = ws
            } else {
                socketContextMap[userId] = SocketContext(ws)
            }
            ws.frameHandler { frame ->
                when {
                    frame.isClose -> handleClose(userId, frame.closeStatusCode().toInt(), frame.closeReason())
                    frame.isText -> handleTextMessage(ws, userId, frame.textData())
                }
            }
            ws.exceptionHandler { err ->
                LOGGER.error("Error during WebSocket communication!", err)
                ws.send(MessageTypes.ERROR, null, err.message ?: "No message.")
            }
            ws.accept()
        }
        webSocketServer.listen(socket.getNotNull("port").intValue(), socket.getNotNull("address").textValue())

        LOGGER.level = Level.toLevel(options.getNotNull("loggingLevel").textValue(), Level.INFO)
        if (!options.isNull("sentryDsn")) {
            val dsn = options.get("sentryDsn").textValue()
            if (!dsn.isBlank() && !dsn.isEmpty())
                Sentry.init(dsn)
        }

        sessionExpirationSeconds = options.getNotNull("sessionExpirationSeconds").intValue()
        val contexts = socketContextMap.values
        statsTimerId = vertx.setPeriodic(options.getNotNull("statsIntervalSeconds").longValue() * 1000) {
            contexts.forEach { context ->
                if (context.resumeTimer == null) // means socket is open
                    context.webSocket.writeTextMessage(JsonStream.serialize(StatsUpdate(this)))
            }
        }

        Runtime.getRuntime().addShutdownHook(thread(start = false) {
            stop()
        })

        startFuture.complete()
    }

    /**
     * @suppress
     */
    override fun stop() {
        LOGGER.info("Closing Basalt Server!")
        if (this::httpServer.isInitialized)
            httpServer.close()
        if (this::webSocketServer.isInitialized)
            webSocketServer.close()
        if (this::socketContextMap.isInitialized) {
            socketContextMap.values.forEach { context ->
                context.webSocket.close(4003)
            }
            socketContextMap.clear()
        }
        statsTimerId?.let { vertx.cancelTimer(it) }
    }

    /**
     * @suppress
     */
    private fun handleClose(userId: String, closeCode: Int, closeMessage: String?) {
        LOGGER.info("Socket closed with close code: {} and reason: {}", closeCode, closeMessage ?: "No specified reason.")
        when (closeCode) {
            4001, 4002, 4003 -> {
                LOGGER.info("Connection to User ID: {} closed manually. Your session cannot be resumed.", userId)
                val context = socketContextMap[userId]!!
                context.players.values.forEach { it.audioSender = null; it.player.destroy()}
                context.resumeTimer?.let { vertx.cancelTimer(it) }
                socketContextMap.remove(userId)
            }
            else -> {
                val context = socketContextMap[userId]!!
                context.resumeTimer = vertx.setTimer((sessionExpirationSeconds * 1000).toLong()) { _ ->
                    LOGGER.info("Destroyed unresumed session for User ID: {} after {} seconds!", userId, sessionExpirationSeconds)
                    context.players.values.forEach { it.audioSender = null; it.player.destroy()}
                    socketContextMap.remove(userId)
                }
            }
        }
    }

    /**
     * @suppress
     */
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
                else -> LOGGER.warn("Unhandled Request with Name: {}", name)

            }
        }
    }

    /**
     * @suppress
     */
    companion object {
        /**
         * @suppress
         */
        private val LOGGER: Logger = LoggerFactory.getLogger(BasaltServer::class.java) as Logger
    }
}

/**
 * @suppress
 */

fun JsonNode.isNull(name: String): Boolean {
    val node = get(name)
    return node == null || node.isNull || node.isMissingNode
}

/**
 * @suppress
 */
fun JsonNode.getNotNull(name: String): JsonNode = if (isNull(name)) throw ConfigurationException(name) else get(name)


/**
 * @suppress
 */
fun WebSocketBase.send(type: MessageType, guildId: String?, data: Any?, key: String? = null) = writeTextMessage(JsonStream.serialize(DispatchResponse(guildId, type.type, data, key)))

/**
 * @suppress
 */
fun WebSocketBase.error(guildId: String?, error: String) = send(MessageTypes.ERROR, guildId, error)

/**
 * @suppress
 */
fun WebSocketBase.error(guildId: String?, error: ErrorResponse) = send(MessageTypes.ERROR, guildId, error.name)