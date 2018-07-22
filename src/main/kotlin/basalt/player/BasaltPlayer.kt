package basalt.player

import basalt.messages.server.DispatchResponse
import basalt.messages.server.JsonTrack
import basalt.messages.server.TrackEndResponse
import com.jsoniter.output.JsonStream
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import io.undertow.websockets.core.WebSockets

class BasaltPlayer internal constructor(val context: SocketContext, val guildId: String, val audioPlayer: AudioPlayer): AudioEventAdapter() {
    init {
        audioPlayer.addListener(this)
    }
    override fun onTrackStart(player: AudioPlayer, track: AudioTrack) {
        val response = DispatchResponse(guildId = guildId, name = "TRACK_STARTED", data = JsonTrack(track))
        WebSockets.sendText(JsonStream.serialize(response), context.channel, null)
    }

    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
        val response = DispatchResponse(guildId = guildId, name = "TRACK_ENDED", data = TrackEndResponse(track, endReason))
        WebSockets.sendText(JsonStream.serialize(response), context.channel, null)
    }

    override fun onTrackException(player: AudioPlayer, track: AudioTrack, exception: FriendlyException) {
        super.onTrackException(player, track, exception)
    }

    override fun onTrackStuck(player: AudioPlayer, track: AudioTrack, thresholdMs: Long) {
        super.onTrackStuck(player, track, thresholdMs)
    }
}