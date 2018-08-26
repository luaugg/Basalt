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

import basalt.player.SocketContext
import basalt.util.AudioTrackUtil
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.github.shredder121.asyncaudio.jda.AsyncPacketProviderFactory

import com.jsoniter.JsonIterator
import com.jsoniter.output.EncodingMode
import com.jsoniter.output.JsonStream
import com.jsoniter.spi.DecodingMode

import com.sedmelluq.discord.lavaplayer.jdaudp.NativeAudioSendFactory
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
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
import io.undertow.Undertow
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import org.slf4j.LoggerFactory
import space.npstr.magma.MagmaApi
import java.io.File

import io.undertow.Handlers.websocket
import io.undertow.websockets.core.WebSocketChannel
import io.undertow.websockets.core.WebSockets
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

import basalt.messages.server.StatsUpdate
import io.undertow.Handlers.path
import io.undertow.io.IoCallback
import io.undertow.io.Sender
import io.undertow.server.HttpServerExchange
import io.undertow.util.Headers
import java.io.IOException

import basalt.player.AudioLoadHandler
import basalt.messages.server.LoadTrackResponse
import basalt.messages.server.JsonTrack

/**
 * Type alias which is equal to `Object2ObjectOpenHashMap<WebSocketChannel, SocketContext>`
 */

typealias SocketContextMap = Object2ObjectOpenHashMap<WebSocketChannel, SocketContext>

/**
 * Server instance which is the starting point for every other part of Basalt.
 *
 * This class is only meant to run/started up once, although it *technically* allows multiple start ups.
 * From this class, the Undertow WebSocket is created and the configuration options are parsed and applied.
 *
 * The Jsoniter options are also set upon this server being started, as well as Magma being setup too.
 *
 * @property mapper The Jackson Object Mapper used to parse the YAML Configuration File.
 * @property listener The [WebSocketListener] which every open WebSocketChannel uses to handle incoming requests.
 * @property contexts The [SocketContextMap] which indexes [SocketContexts][SocketContext] by Undertow WebSocketChannels.
 * @property loadChunkSize The amount of identifier load results to fit into each `LOAD_TRACK_CHUNK` event (1+).
 * @property password The password used to successfully open a WebSocket Connection to this BasaltServer instance.
 * @property magma The raw MagmaApi instance used to actually send and update voice data/state.
 * @property sourceManager The Lavaplayer SourceManager used to actually load sources.
 * @property socket The Undertow WebSocket instance.
 * @property trackUtil The [AudioTrackUtil] instance used externally to encode tracks and vice versa.
 * @property statsExecutor The ScheduledExecutorService used to send statistics to each connected channel.
 * @property statsTask The task that sends statistics every provided period.
 *
 * @author Sam Pritchard
 * @since 1.0
 * @constructor Simply constructs a BasaltServer instance with no additional information.
 */

class BasaltServer: AbstractVerticle() {
    private val mapper = ObjectMapper(YAMLFactory())
    private val listener = WebSocketListener(this)
    internal val contexts = SocketContextMap()

    /**
     * @suppress
     */
    internal var bufferDurationMs: Int = -1
    internal var loadChunkSize: Int = -1
    internal lateinit var magma: MagmaApi
    internal lateinit var sourceManager: AudioPlayerManager
    internal val trackUtil = AudioTrackUtil(this)

    private lateinit var password: String
    private lateinit var socket: Undertow

    private val statsExecutor = Executors.newSingleThreadScheduledExecutor()
    private var statsTask: ScheduledFuture<*>? = null

    /**
     * Called by Vert.x when this Verticle is deployed (which it is in the Main class).
     */
    override fun start(startFuture: Future<Void>) {
        JsonIterator.setMode(DecodingMode.DYNAMIC_MODE_AND_MATCH_FIELD_WITH_HASH)
        JsonStream.setMode(EncodingMode.DYNAMIC_MODE)
        val config = mapper.readTree(File("basalt.yml"))
        val basalt = config["basalt"]!!
        val http = basalt["http"]!!
        val ws = basalt["socket"]!!
        val sources = basalt["sources"]!!
        val dsn = basalt["sentryDsn"]
        if (dsn != null)
            Sentry.init(dsn.textValue())
        password = basalt["password"]?.textValue() ?: ""
        sourceManager = DefaultAudioPlayerManager()
        bufferDurationMs = basalt["bufferDurationMs"]!!.intValue()
        loadChunkSize = basalt["loadChunkSize"]!!.intValue()
        if (loadChunkSize < 1) {
            LOGGER.error("The size for Load Chunks must be larger than 0! Current Value: {}", loadChunkSize)
            throw UnsupportedOperationException(loadChunkSize.toString())
        }

        val statsInterval = basalt["statsInterval"]!!.intValue()
        if (statsInterval < 5) {
            LOGGER.error("Cannot send statistics at a more frequent rate than every 5 seconds!")
            throw UnsupportedOperationException("Stats interval is too precise or out of bounds! $statsInterval")
        }

        statsTask = statsExecutor.scheduleAtFixedRate({
            try {
                LOGGER.debug("Sending statistics to {} channels!", contexts.keys.size)
                val stats = JsonStream.serialize(StatsUpdate(this))
                contexts.keys.forEach {
                    channel ->
                    if (channel.isOpen)
                        WebSockets.sendText(stats, channel, null)
                }
            } catch (err: Throwable) {
                LOGGER.error("Error thrown when attempting to send stats!", err)
            }
        }, 0, statsInterval.toLong(), TimeUnit.SECONDS)

        if (sources["youtube"]?.booleanValue() == true) {
            val manager = YoutubeAudioSourceManager(true)
            val playlistLimit = basalt["playlistPageLimit"]
            if (playlistLimit != null)
                manager.setPlaylistPageCount(playlistLimit.intValue())
            sourceManager.registerSourceManager(manager)
        }
        if (sources["soundcloud"]?.booleanValue() == true)
            sourceManager.registerSourceManager(SoundCloudAudioSourceManager(true))
        if (sources["bandcamp"]?.booleanValue() == true)
            sourceManager.registerSourceManager(BandcampAudioSourceManager())
        if (sources["twitch"]?.booleanValue() == true)
            sourceManager.registerSourceManager(TwitchStreamAudioSourceManager())
        if (sources["vimeo"]?.booleanValue() == true)
            sourceManager.registerSourceManager(VimeoAudioSourceManager())
        if (sources["mixer"]?.booleanValue() == true)
            sourceManager.registerSourceManager(BeamAudioSourceManager())
        if (sources["http"]?.booleanValue() == true)
            sourceManager.registerSourceManager(HttpAudioSourceManager())
        if (sources["local"]?.booleanValue() == true)
            sourceManager.registerSourceManager(LocalAudioSourceManager())

        val authHandler: ((HttpServerExchange) -> Boolean) = {
            exchange ->
            val authHeaders = exchange.requestHeaders[Headers.AUTHORIZATION]
            val auth = if (authHeaders == null || authHeaders.isEmpty()) "" else authHeaders.first
            auth == password
        }

        val ioCallback = object: IoCallback {
            override fun onComplete(exchange: HttpServerExchange, sender: Sender) {
                exchange.endExchange()
            }

            override fun onException(exchange: HttpServerExchange, sender: Sender, exception: IOException) {
                LOGGER.error("Error when sending response content!", exception)
                exchange.endExchange() // always have to clean up even when exceptions are thrown
            }
        }

        val path = path()
        path.addPrefixPath("/loadidentifiers") { exchange ->
            if (!authHandler(exchange)) {
                LOGGER.warn("Invalid Authorization Header!")
                exchange.statusCode = 401
                exchange.responseHeaders.add(Headers.WWW_AUTHENTICATE, "None realm=\"Loading of identifiers. Supply server password.\"")
                exchange.responseHeaders.add(Headers.CONTENT_TYPE, "text/plain")
                exchange.responseSender.send("Unauthorized -- Invalid Authorization Header", ioCallback)
                return@addPrefixPath
            }
            val identifiers = exchange.queryParameters["identifier"]
            if (identifiers == null) {
                LOGGER.warn("Missing Identifier Query Parameters!")
                exchange.statusCode = 400
                exchange.responseHeaders.add(Headers.CONTENT_TYPE, "text/plain")
                exchange.responseSender.send("Bad Request -- Missing identifiers!", ioCallback)
                return@addPrefixPath
            }
            val runnable = {
                val response = arrayOfNulls<LoadTrackResponse>(identifiers.size)
                identifiers.forEachIndexed {
                    index, identifier ->
                    AudioLoadHandler(this).load(identifier)
                            .thenApply { LoadTrackResponse(it) }
                            .thenAccept { response[index] = it }
                            .thenAccept {
                                if (index + 1 == identifiers.size) {
                                    exchange.responseHeaders.add(Headers.CONTENT_TYPE, "application/json")
                                    exchange.responseSender.send(JsonStream.serialize(response), ioCallback)
                                }
                            }
                }
            }
            if (exchange.isInIoThread)
                exchange.dispatch(runnable)
            else
                runnable()
        }

        path.addPrefixPath("/decodetracks") { exchange ->
            if (!authHandler(exchange)) {
                LOGGER.warn("Invalid Authorization Header!")
                exchange.statusCode = 401
                exchange.responseHeaders.add(Headers.WWW_AUTHENTICATE, "None realm=\"Decoding of tracks. Supply server password.\"")
                exchange.responseHeaders.add(Headers.CONTENT_TYPE, "text/plain")
                exchange.responseSender.send("Unauthorized -- Invalid Authorization Header", ioCallback)
                return@addPrefixPath
            }
            val tracks = exchange.queryParameters["track"]
            if (tracks == null) {
                LOGGER.warn("Missing Encoded Track Strings!")
                exchange.statusCode = 400
                exchange.responseHeaders.add(Headers.CONTENT_TYPE, "text/plain")
                exchange.responseSender.send("Bad Request -- Missing tracks!", ioCallback)
                return@addPrefixPath
            }
            val runnable = {
                try {
                    val response = arrayOfNulls<JsonTrack>(tracks.size)
                    tracks.forEachIndexed { index, track ->
                        response[index] = JsonTrack(trackUtil.toAudioTrack(track))
                    }
                    exchange.responseHeaders.add(Headers.CONTENT_TYPE, "application/json")
                    exchange.responseSender.send(JsonStream.serialize(response), ioCallback)
                } catch (err: Throwable) {
                    LOGGER.error("Error decoding tracks!", err)
                    exchange.statusCode = 500
                    exchange.responseHeaders.add(Headers.CONTENT_TYPE, "text/plain")
                    exchange.responseSender.send("Internal Server Error", ioCallback)
                }
            }
            if (exchange.isInIoThread)
                exchange.dispatch(runnable)
            else
                runnable()
        }

        val websocketHandler = websocket { exchange, channel ->
            val auth = exchange.getRequestHeader("Authorization") ?: ""
            val userId = exchange.getRequestHeader("User-Id")
            if (userId == null) {
                LOGGER.warn("Missing User-Id Header!")
                WebSockets.sendClose(4001, "Missing Headers", channel, null)
                exchange.endExchange()
                return@websocket
            }
            if (auth != password) {
                LOGGER.warn("Invalid Authorization Header!")
                WebSockets.sendClose(4002, "Invalid Headers", channel, null)
                exchange.endExchange()
                return@websocket
            }
            contexts[channel] = SocketContext(this, channel, userId)
            val host = channel.sourceAddress
                    .toString()
                    .replaceFirst("/", "")
                    .replaceFirst("0:0:0:0:0:0:0:1", "localhost")
            LOGGER.info("Connection opened from {} using WebSocket Protocol Version {}", host, channel.version.toHttpHeaderValue())
            channel.receiveSetter.set(listener)
            channel.resumeReceives()
        }

        magma = MagmaApi.of { AsyncPacketProviderFactory.adapt(NativeAudioSendFactory(bufferDurationMs)) }
        socket = Undertow.builder()
                .addHttpListener(http["port"]!!.intValue(), http["host"]!!.textValue(), path)
                .addHttpListener(ws["port"]!!.intValue(), ws["host"]!!.textValue(), websocketHandler)
                .build()
        socket.start()
        startFuture.complete()
    }

    /**
     * Called when the Verticle is stopped, which Basalt doesn't do normally except in the shutdown hook.
     */
    override fun stop() {
        LOGGER.info("Closing the Basalt Server ({} connected sockets)", contexts.size)
        contexts.keys.forEach { WebSockets.sendClose(1000, "Server shutting down!", it, null) }
        magma.shutdown()
        socket.stop()
        statsTask?.cancel(false)
        statsExecutor.shutdown()
    }

    /**
     * @suppress
     */
    companion object {
        /**
         * @suppress
         */
        private val LOGGER = LoggerFactory.getLogger(BasaltServer::class.java)
    }
}