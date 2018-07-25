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

import basalt.server.BasaltServer
import com.jsoniter.annotation.JsonIgnore
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioTrack

@Suppress("UNUSED")
class TrackExceptionResponse internal constructor(@field:JsonIgnore private val server: BasaltServer,
                                                  @field:JsonIgnore private val tr: AudioTrack,
                                                  @field:JsonIgnore private val exc: FriendlyException) {
    val track = server.trackUtil.fromAudioTrack(tr)
    val exception = ExceptionData()
    inner class ExceptionData {
        val severity = exc.severity.name
        val message = exc.message
    }
}