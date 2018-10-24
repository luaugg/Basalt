package basalt.player

import basalt.messages.server.DispatchResponse
import basalt.messages.server.PlayerUpdate
import com.jsoniter.output.JsonStream
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.*
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import io.vertx.core.Vertx
import io.vertx.core.http.WebSocketBase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class BasaltPlayer(private val vertx: Vertx, private val webSocket: WebSocketBase, val player: AudioPlayer, val guildId: String): AudioEventAdapter() {
    private var updateSchedulerId: Long? = null

    override fun onTrackStart(player: AudioPlayer, track: AudioTrack) {
        GlobalScope.launch {
            val response = DispatchResponse(guildId, "TRACK_START", TrackStartEvent(player, track))
            webSocket.writeTextMessage(JsonStream.serialize(response))
            updateSchedulerId = vertx.setPeriodic(5000) {
                webSocket.writeTextMessage(JsonStream.serialize(PlayerUpdate(guildId, player.playingTrack?.position ?: 0, System.currentTimeMillis())))
            }
        }
    }

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
        GlobalScope.launch {
            val response = DispatchResponse(guildId, "TRACK_END", TrackEndEvent(player, track, endReason))
            webSocket.writeTextMessage(JsonStream.serialize(response))
            updateSchedulerId?.let { vertx.cancelTimer(it); updateSchedulerId = null }
        }
    }

    override fun onTrackException(player: AudioPlayer, track: AudioTrack, exception: FriendlyException) {
        GlobalScope.launch {
            val response = DispatchResponse(guildId, "TRACK_EXCEPTION", TrackExceptionEvent(player, track, exception))
            webSocket.writeTextMessage(JsonStream.serialize(response))
        }
    }

    override fun onTrackStuck(player: AudioPlayer, track: AudioTrack, thresholdMs: Long) {
        GlobalScope.launch {
            val response = DispatchResponse(guildId, "TRACK_STUCK", TrackStuckEvent(player, track, thresholdMs))
            webSocket.writeTextMessage(JsonStream.serialize(response))
        }
    }

    override fun onPlayerPause(player: AudioPlayer) {
        GlobalScope.launch {
            val response = DispatchResponse(guildId, "PLAYER_PAUSE", true)
            webSocket.writeTextMessage(JsonStream.serialize(response))
        }
    }

    override fun onPlayerResume(player: AudioPlayer) {
        GlobalScope.launch {
            val response = DispatchResponse(guildId, "PLAYER_PAUSE", false)
            webSocket.writeTextMessage(JsonStream.serialize(response))
        }
    }
}