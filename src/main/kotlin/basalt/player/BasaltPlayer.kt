package basalt.player

import basalt.messages.server.*
import com.jsoniter.output.JsonStream
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import io.undertow.websockets.core.WebSockets
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class BasaltPlayer internal constructor(private val context: SocketContext, private val guildId: String,
                                        internal val audioPlayer: AudioPlayer): AudioEventAdapter() {

    private val threadPool = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var updateTask: ScheduledFuture<*>? = null

    init {
        audioPlayer.addListener(this)
    }

    override fun onTrackStart(player: AudioPlayer, track: AudioTrack) {
        val trackData = context.server.trackUtil.fromAudioTrack(track)
        val response = DispatchResponse(guildId = guildId, name = "TRACK_STARTED", data = TrackPair(trackData, track))
        WebSockets.sendText(JsonStream.serialize(response), context.channel, null)
        updateTask = threadPool.scheduleAtFixedRate({
            if (!context.channel.isOpen) {
                updateTask?.cancel(false)
                updateTask = null
                return@scheduleAtFixedRate
            }
            val resp = PlayerUpdate(guildId, track.position, System.currentTimeMillis())
            WebSockets.sendText(JsonStream.serialize(resp), context.channel, null)
        }, 0, 5, TimeUnit.SECONDS)
    }

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
        val response = DispatchResponse(guildId = guildId, name = "TRACK_ENDED", data = TrackEndResponse(context.server, track, endReason))
        WebSockets.sendText(JsonStream.serialize(response), context.channel, null)
        updateTask?.cancel(false)
        updateTask = null
    }

    override fun onTrackException(player: AudioPlayer, track: AudioTrack, exception: FriendlyException) {
        val response = DispatchResponse(guildId = guildId, name = "TRACK_EXCEPTION", data = TrackExceptionResponse(context.server, track, exception))
        WebSockets.sendText(JsonStream.serialize(response), context.channel, null)
    }

    override fun onTrackStuck(player: AudioPlayer, track: AudioTrack, thresholdMs: Long) {
        val response = DispatchResponse(guildId = guildId, name = "TRACK_STUCK", data = TrackStuckResponse(context.server, track, thresholdMs))
        WebSockets.sendText(JsonStream.serialize(response), context.channel, null)
    }

    override fun onPlayerPause(player: AudioPlayer?) {
        val response = DispatchResponse(guildId = guildId, name = "PLAYER_PAUSE", data = true)
        WebSockets.sendText(JsonStream.serialize(response), context.channel, null)
    }

    override fun onPlayerResume(player: AudioPlayer?) {
        val response = DispatchResponse(guildId = guildId, name = "PLAYER_PAUSE", data = false)
        WebSockets.sendText(JsonStream.serialize(response), context.channel, null)
    }
}