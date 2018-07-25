package basalt.messages.server

import com.sedmelluq.discord.lavaplayer.track.AudioTrack

@Suppress("UNUSED")
class TrackPair internal constructor(val data: String, tr: AudioTrack) {
    val track = JsonTrack(tr)
}