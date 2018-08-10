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
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap

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
 * @property password The password used to successfully open a WebSocket Connection to this BasaltServer instance.
 * @property magma The raw MagmaApi instance used to actually send and update voice data/state.
 * @property sourceManager The Lavaplayer SourceManager used to actually load sources.
 * @property socket The Undertow WebSocket instance.
 * @property trackUtil The [AudioTrackUtil] instance used externally to encode tracks and vice versa.
 *
 * @author Sam Pritchard
 * @since 1.0
 * @constructor Simply constructs a BasaltServer instance with no additional information.
 */

class BasaltServer: AbstractVerticle() {
    private val mapper = ObjectMapper(YAMLFactory())
    private val listener = WebSocketListener(this)
    /**
     * @suppress
     */
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

    /**
     * Called by Vert.x when this Verticle is deployed (which it is in the Main class).
     */
    override fun start(startFuture: Future<Void>) {
        JsonIterator.setMode(DecodingMode.DYNAMIC_MODE_AND_MATCH_FIELD_WITH_HASH)
        JsonStream.setMode(EncodingMode.DYNAMIC_MODE)
        val config = mapper.readTree(File("basalt.yml"))
        val basalt = config["basalt"]!!
        val ws = basalt["socket"]!!
        val sources = basalt["sources"]!!
        val dsn = basalt["sentryDsn"]
        if (dsn != null)
            Sentry.init(dsn.textValue())
        password = basalt["password"]!!.textValue()
        sourceManager = DefaultAudioPlayerManager()
        bufferDurationMs = basalt["bufferDurationMs"]!!.intValue()
        loadChunkSize = basalt["loadChunkSize"]!!.intValue()
        if (loadChunkSize < 1) {
            LOGGER.error("The size for Load Chunks must be larger than 0! Current Value: {}", loadChunkSize)
            throw IllegalArgumentException(loadChunkSize.toString())
        }

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

        val websocketHandler = websocket {exchange, channel ->
            val auth = exchange.getRequestHeader("Authorization")
            val userId = exchange.getRequestHeader("User-Id")
            if (auth == null) {
                LOGGER.error("Missing Authorization Header!")
                channel.closeCode = 4001
                channel.closeReason = "Missing Headers"
                channel.close()
                return@websocket
            }
            if (userId == null) {
                LOGGER.error("Missing User-Id Header!")
                channel.closeCode = 4001
                channel.closeReason = "Missing Headers"
                channel.close()
                return@websocket
            }
            if (auth != password) {
                LOGGER.error("Invalid Authorization Header!")
                channel.closeCode = 4002
                channel.closeReason = "Invalid Headers"
                channel.close()
                return@websocket
            }
            val host = channel.sourceAddress
                    .toString()
                    .replaceFirst("/", "")
                    .replaceFirst("0:0:0:0:0:0:0:1", "localhost")
            LOGGER.info("Connection opened from {} using WebSocket Protocol Version {}", host, channel.version.toHttpHeaderValue())
            channel.receiveSetter.set(listener)
            channel.resumeReceives()
        }
        magma = MagmaApi.of {AsyncPacketProviderFactory.adapt(NativeAudioSendFactory(bufferDurationMs))}
        socket = Undertow.builder()
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
        magma.shutdown()
        socket.stop()
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