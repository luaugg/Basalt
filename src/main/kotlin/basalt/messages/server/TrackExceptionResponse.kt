package basalt.messages.server

import com.jsoniter.annotation.JsonIgnore
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioTrack

@Suppress("UNUSED")
class TrackExceptionResponse internal constructor(@field:JsonIgnore private val tr: AudioTrack,
                                                  @field:JsonIgnore private val exc: FriendlyException) {
    val track = JsonTrack(tr)
    val exception = ExceptionData()
    inner class ExceptionData {
        val severity = exc.severity.name
        val message = exc.message
    }
}