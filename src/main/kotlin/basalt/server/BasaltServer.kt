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

class BasaltServer: AbstractVerticle() {
    private val mapper = ObjectMapper(YAMLFactory())
    private val listener = WebSocketListener(this)
    internal val contexts = Object2ObjectOpenHashMap<WebSocketChannel, SocketContext>()
    internal var bufferDurationMs: Int = -1
    private lateinit var password: String
    internal lateinit var magma: MagmaApi
    internal lateinit var sourceManager: AudioPlayerManager
    private lateinit var socket: Undertow
    internal val trackUtil = AudioTrackUtil(this)

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

    override fun stop() {
        LOGGER.info("Closing the Basalt Server ({} connected sockets)", contexts.size)
        magma.shutdown()
        socket.stop()
    }
    companion object {
        private val LOGGER = LoggerFactory.getLogger(BasaltServer::class.java)
    }
}