package basalt.messages.server

import basalt.player.LoadResult
import com.jsoniter.annotation.JsonIgnore

@Suppress("UNUSED")
class LoadTrackResponse internal constructor(@field:JsonIgnore private val result: LoadResult) {
    val playlistInfo = if (result.playlistInfo != null) PlaylistInfo() else null
    val loadType = result.result
    val tracks = result.tracks
    inner class PlaylistInfo {
        @JsonIgnore private val info = this@LoadTrackResponse.result.playlistInfo
        val name = info!!.first
        val selectedTrack = info!!.second
    }
}