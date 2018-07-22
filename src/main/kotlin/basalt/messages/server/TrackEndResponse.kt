package basalt.messages.server

import basalt.server.BasaltServer
import com.jsoniter.annotation.JsonIgnore
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason

@Suppress("UNUSED")
class TrackEndResponse internal constructor(@field:JsonIgnore private val server: BasaltServer,
                                            @field:JsonIgnore private val tr: AudioTrack,
                                            @field:JsonIgnore private val rs: AudioTrackEndReason) {
    val track = server.trackUtil.fromAudioTrack(tr)
    val reason = ReasonData()
    inner class ReasonData {
        val type = rs.name
        val mayStartNext = rs.mayStartNext
    }
}