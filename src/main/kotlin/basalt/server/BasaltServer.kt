package basalt.server

import basalt.player.AudioLoadHandler
import basalt.player.BasaltPlayer
import basalt.server.messages.LoadTrackResponse
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
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.Router
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap

class BasaltServer: AbstractVerticle() {
    private val mapper = ObjectMapper(YAMLFactory())
    internal val players: Map<WebSocketChannel, BasaltPlayer> = Object2ObjectOpenHashMap()
    lateinit internal var password: String
    lateinit internal var magma: MagmaApi
    lateinit internal var sourceManager: AudioPlayerManager
    private lateinit var socket: Undertow
    private lateinit var server: HttpServer
    private lateinit var router: Router
    val trackUtil = AudioTrackUtil(this)
    override fun start(startFuture: Future<Void>) {
        JsonIterator.setMode(DecodingMode.DYNAMIC_MODE_AND_MATCH_FIELD_WITH_HASH)
        JsonStream.setMode(EncodingMode.DYNAMIC_MODE)
        val config = mapper.readTree(File("basalt.yml"))
        val basalt = config["basalt"]!!
        val server = basalt["server"]!!
        val ws = server["socket"]!!
        val rest = server["rest"]!!
        val sources = basalt["sources"]!!
        val dsn = basalt["sentryDsn"]
        if (dsn != null)
            Sentry.init(dsn.textValue())
        password = basalt["password"]!!.textValue()
        sourceManager = DefaultAudioPlayerManager()

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
                return@websocket
            }
            if (userId == null) {
                LOGGER.error("Missing User-Id Header!")
                return@websocket
            }
            if (auth != basalt["password"]!!.textValue()) {
                LOGGER.error("Invalid Authorization Header!")
                return@websocket
            }
            val host = channel.sourceAddress
                    .toString()
                    .replaceFirst("/", "")
                    .replaceFirst("0:0:0:0:0:0:0:1", "localhost")
            LOGGER.info("Connection opened from {} using WebSocket Protocol Version {}", host, channel.version.toHttpHeaderValue())
            channel.receiveSetter.set(WebSocketListener(this))
            channel.resumeReceives()
        }

        this.server = vertx.createHttpServer()
        router = Router.router(vertx)
        initRoutes()
        magma = MagmaApi.of {AsyncPacketProviderFactory.adapt(NativeAudioSendFactory(basalt["bufferDurationMs"]!!.intValue()))}
        socket = Undertow.builder()
                .addHttpListener(ws["port"]!!.intValue(), ws["host"]!!.textValue(), websocketHandler)
                .build()
        socket.start()
        this.server.requestHandler(router::accept).listen(rest["port"]!!.intValue(), rest["address"]!!.textValue())
        startFuture.complete()
    }
    override fun stop() {
        LOGGER.info("Closing the Basalt Server ({} connected players)", players.size)
        magma.shutdown()
        socket.stop()
        server.close()
    }
    private fun checkAuthorization(auth: String?, response: HttpServerResponse): Boolean {
        if (auth == null) {
            response.statusCode = 401
            response.statusMessage = "Unauthorized"
            response.end()
            return true
        }
        if (auth != password) {
            response.statusCode = 403
            response.statusMessage = "Forbidden"
            response.end()
            return true
        }
        return false
    }
    private fun initRoutes() {
        router.exceptionHandler { LOGGER.error("Error during HTTP Response!", it) }
        router.get("/loadtracks/:identifier").handler { context ->
            val request = context.request()
            val response = context.response()
            val auth = request.getHeader("Authorization")
            if (checkAuthorization(auth, response))
                return@handler
            val identifier = context.request().getParam("identifier")
            AudioLoadHandler(this)
                    .load(identifier)
                    .thenApply { JsonStream.serialize(LoadTrackResponse(it)) }
                    .thenAccept { json ->
                        response.statusCode = 200
                        response.statusMessage = "OK"
                        response.putHeader("content-type", "application/json")
                        response.end(json)
                    }
        }
        router.get("/decodetrack/:identifier").handler { context ->

        }
    }
    companion object {
        private val LOGGER = LoggerFactory.getLogger(BasaltServer::class.java)
    }
}