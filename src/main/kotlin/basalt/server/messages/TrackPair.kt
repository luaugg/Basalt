package basalt.server.messages

import com.jsoniter.annotation.JsonIgnore
import com.sedmelluq.discord.lavaplayer.track.AudioTrack

@Suppress("UNUSED")
data class TrackPair(val data: String, @field:JsonIgnore private val tr: AudioTrack) {
    val track = TrackPairData()
    @Suppress("UNUSED")
    inner class TrackPairData {
        @JsonIgnore private val info = this@TrackPair.tr.info
        val title: String = info.title
        val author: String = info.author
        val identifier: String = info.identifier
        val uri: String = info.uri
        val isStream: Boolean = info.isStream
        val length: Long = info.length
    }
}