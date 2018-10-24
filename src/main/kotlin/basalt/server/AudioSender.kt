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
package basalt.server

import basalt.player.BasaltPlayer
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame
import net.dv8tion.jda.core.audio.AudioSendHandler

/**
 * The AudioSendHandler used by Magma to actually send audio in the first place.
 *
 * @since 4.0.0
 * @author Sam Pritchard
 * @property player The [BasaltPlayer] instance from which to provide data.
 * @property frame A frame representing 20ms of Opus-encoded audio data.
 */

class AudioSender(val player: BasaltPlayer): AudioSendHandler {
    private var frame: AudioFrame? = null

    /**
     * @suppress
     */
    override fun provide20MsAudio() = frame?.data

    /**
     * @suppress
     */
    override fun canProvide() = frame?.let { frame = player.player.provide(); frame != null } == true

    /**
     * @suppress
     */
    override fun isOpus() = true
}