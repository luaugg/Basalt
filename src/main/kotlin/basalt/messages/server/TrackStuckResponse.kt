package basalt.messages.server

import basalt.server.BasaltServer
import com.jsoniter.annotation.JsonIgnore
import com.sedmelluq.discord.lavaplayer.track.AudioTrack

@Suppress("UNUSED")
class TrackStuckResponse internal constructor(@field:JsonIgnore private val server: BasaltServer,
                                              @field:JsonIgnore private val tr: AudioTrack,
                                              val thresholdMs: Long) {
    val serverBufferDuration = server.bufferDurationMs
    val track = JsonTrack(tr)
}