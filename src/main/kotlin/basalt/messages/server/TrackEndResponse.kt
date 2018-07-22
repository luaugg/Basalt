package basalt.messages.server

import com.jsoniter.annotation.JsonIgnore
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason

@Suppress("UNUSED")
class TrackEndResponse(@field:JsonIgnore private val tr: AudioTrack, @field:JsonIgnore private val rs: AudioTrackEndReason) {
    val track = JsonTrack(tr)
    val reason = ReasonData()
    inner class ReasonData {
        val type = rs.name
        val mayStartNext = rs.mayStartNext
    }
}