package basalt.messages.server

import com.jsoniter.annotation.JsonIgnore
import com.sedmelluq.discord.lavaplayer.track.AudioTrack

@Suppress("UNUSED")
class JsonTrack internal constructor(@field:JsonIgnore private val track: AudioTrack) {
    @JsonIgnore private val info = track.info!!
    val title = info.title!!
    val author = info.author!!
    val identifier = info.identifier!!
    val uri = info.uri!!
    val stream = info.isStream
    val seekable = track.isSeekable
    val position = track.position
    val length = info.length
}