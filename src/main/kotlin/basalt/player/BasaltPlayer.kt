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
package basalt.player

import basalt.messages.server.DispatchResponse
import basalt.messages.server.PlayerUpdate
import basalt.server.AudioSender
import com.jsoniter.output.JsonStream
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.*
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import io.vertx.core.Vertx
import io.vertx.core.http.WebSocketBase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * A player class which contains methods that react to lavaplayer events. Represents a unique audio stream for a specific guild.
 *
 * @author Sam Pritchard
 * @since 4.0.0
 *
 * @property vertx The Vert.x instance used for timers.
 * @property webSocket The raw WebSocket connection between clients and Basalt.
 * @property player The lavaplayer AudioPlayer instance used to actually play things.
 * @property guildId The unique ID of the Guild this player serves audio to.
 * @property updateSchedulerId The possibly-null ID of the Vert.x timer that sends updates while tracks are playing.
 * @property audioSender The [AudioSender] instance responsible for providing the raw audio.
 */

class BasaltPlayer(private val vertx: Vertx, private val webSocket: WebSocketBase, val player: AudioPlayer, val guildId: String): AudioEventAdapter() {
    private var updateSchedulerId: Long? = null
    var audioSender: AudioSender? = null

    /**
     * @suppress
     */
    override fun onTrackStart(player: AudioPlayer, track: AudioTrack) {
        GlobalScope.launch {
            val response = DispatchResponse(guildId, "TRACK_START", TrackStartEvent(player, track))
            webSocket.writeTextMessage(JsonStream.serialize(response))
            updateSchedulerId = vertx.setPeriodic(5000) {
                webSocket.writeTextMessage(JsonStream.serialize(PlayerUpdate(guildId, player.playingTrack?.position ?: 0, System.currentTimeMillis())))
            }
        }
    }

    /**
     * @suppress
     */
    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
        GlobalScope.launch {
            val response = DispatchResponse(guildId, "TRACK_END", TrackEndEvent(player, track, endReason))
            webSocket.writeTextMessage(JsonStream.serialize(response))
            updateSchedulerId?.let { vertx.cancelTimer(it); updateSchedulerId = null }
        }
    }

    /**
     * @suppress
     */
    override fun onTrackException(player: AudioPlayer, track: AudioTrack, exception: FriendlyException) {
        GlobalScope.launch {
            val response = DispatchResponse(guildId, "TRACK_EXCEPTION", TrackExceptionEvent(player, track, exception))
            webSocket.writeTextMessage(JsonStream.serialize(response))
        }
    }

    /**
     * @suppress
     */
    override fun onTrackStuck(player: AudioPlayer, track: AudioTrack, thresholdMs: Long) {
        GlobalScope.launch {
            val response = DispatchResponse(guildId, "TRACK_STUCK", TrackStuckEvent(player, track, thresholdMs))
            webSocket.writeTextMessage(JsonStream.serialize(response))
        }
    }

    /**
     * @suppress
     */
    override fun onPlayerPause(player: AudioPlayer) {
        GlobalScope.launch {
            val response = DispatchResponse(guildId, "PLAYER_PAUSE", true)
            webSocket.writeTextMessage(JsonStream.serialize(response))
        }
    }

    /**
     * @suppress
     */
    override fun onPlayerResume(player: AudioPlayer) {
        GlobalScope.launch {
            val response = DispatchResponse(guildId, "PLAYER_PAUSE", false)
            webSocket.writeTextMessage(JsonStream.serialize(response))
        }
    }
}