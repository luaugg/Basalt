/*
Copyright 2018 Sam Pritchard

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package basalt.messages.server

import basalt.player.LoadResult
import com.jsoniter.annotation.JsonIgnore
import com.jsoniter.annotation.JsonUnwrapper
import com.jsoniter.output.JsonStream

@Suppress("UNUSED")
class LoadTrackResponse internal constructor(@field:JsonIgnore private val result: LoadResult) {
    @JsonIgnore private val loadType = result.result.name
    @JsonIgnore private val tracks = result.tracks

    @JsonUnwrapper
    fun unwrapResponse(stream: JsonStream) {
        with (stream) {
            writeObjectField("loadType")
            writeVal(loadType)
            writeMore()
            writeObjectField("tracks")
            writeVal(tracks)
            result.playlistInfo?.let {
                writeMore()
                writeObjectField("playlistInfo")
                writeVal(PlaylistInfo())
            }
        }
    }

    inner class PlaylistInfo {
        @JsonIgnore private val info = this@LoadTrackResponse.result.playlistInfo
        val name = info!!.first
        val selectedTrack = info!!.second
    }
}