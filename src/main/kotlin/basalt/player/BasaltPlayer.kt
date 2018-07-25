package basalt.player

import basalt.messages.server.*
import com.jsoniter.output.JsonStream
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import io.undertow.websockets.core.WebSockets
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * A player class associated with a [SocketContext] and a Guild ID, with a "lifecycle" determined by incoming requests.
 *
 * This class is created upon an `initialize` request, and destroyed as well as cleaned up upon a `destroy` request.
 * Additionally, as tracks start, end, are paused, resumed, etc. this player will send events back across the WebSocket Connection.
 *
 * For example, when a track starts, Basalt:
 * - Firstly encodes the track and creates a dispatch event (`TRACK_STARTED` name), additional info, etc.
 * - Sends the event across the WebSocketChannel (the connection between a client and Basalt).
 * - Starts a scheduled task which sends `PLAYER_UPDATE` events every 5 seconds until the task is cancelled.
 *
 * When a track ends, Basalt:
 * - Dispatches a `TRACK_ENDED` event across the WebSocketChannel
 * - Cancels the update task from earlier on.
 * - Sets the task to `null`.
 *
 * @property context The [SocketContext] that this player belongs to.
 * @property guildId The ID of the Guild that audio is sent to.
 * @property audioPlayer A Lavaplayer AudioPlayer instance.
 * @property threadPool The Java ScheduledExecutorService used to schedule the `PLAYER_UPDATE` task.
 * @property updateTask The actual update task itself that is eventually cancelled.
 *
 * @author Sam Pritchard
 * @since 1.0
 * @constructor Constructs a BasaltPlayer based on a provided [SocketContext], a Guild ID and an AudioPlayer.
 */

class BasaltPlayer internal constructor(internal val context: SocketContext, private val guildId: String,
                                        internal val audioPlayer: AudioPlayer): AudioEventAdapter() {

    private val threadPool = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var updateTask: ScheduledFuture<*>? = null

    init {
        audioPlayer.addListener(this)
    }

    /**
     * Fired by Lavaplayer when an AudioTrack starts.
     *
     * This sends a `TRACK_STARTED` event, **wrapped in a Dispatch Response**, across a WebSocketChannel.
     * Additionally, it starts an Update Task which sends `PLAYER_UPDATE` events
     * that **are NOT** wrapped in a Dispatch Response every 5 seconds.
     *
     * @param player The AudioPlayer object itself.
     * @param track the AudioTrack that was started.
     */
    override fun onTrackStart(player: AudioPlayer, track: AudioTrack) {
        val trackData = context.server.trackUtil.fromAudioTrack(track)
        val response = DispatchResponse(context, guildId, "TRACK_STARTED", TrackPair(trackData, track))
        WebSockets.sendText(JsonStream.serialize(response), context.channel, null)
        updateTask = threadPool.scheduleAtFixedRate({
            if (!context.channel.isOpen) {
                updateTask?.cancel(false)
                updateTask = null
                return@scheduleAtFixedRate
            }
            val resp = PlayerUpdate(guildId, track.position, System.currentTimeMillis())
            WebSockets.sendText(JsonStream.serialize(resp), context.channel, null)
        }, 0, 5, TimeUnit.SECONDS)
    }

    /**
     * Fired by Lavaplayer when an AudioTrack ends.
     *
     * This sends a `TRACK_ENDED` event, **wrapped in a Dispatch Response**, across a WebSocketChannel.
     * It also cancels the previously-started update task and sets it to `null`.
     *
     * @param player The AudioPlayer object itself.
     * @param track The AudioTrack that was ended.
     * @param endReason The reason why the track ended.
     */
    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
        val response = DispatchResponse(context, guildId, "TRACK_ENDED", TrackEndResponse(context.server, track, endReason))
        WebSockets.sendText(JsonStream.serialize(response), context.channel, null)
        updateTask?.cancel(false)
        updateTask = null
    }

    /**
     * Fired by Lavaplayer when an exception was thrown during playback of an AudioTrack.
     *
     * This sends a `TRACK_EXCEPTION` event, **wrapped in a Dispatch Response**, across a WebSocketChannel.
     *
     * @param player The AudioPlayer object itself.
     * @param track The AudioTrack that was playing.
     * @param exception The exception that was thrown.
     */
    override fun onTrackException(player: AudioPlayer, track: AudioTrack, exception: FriendlyException) {
        val response = DispatchResponse(context, guildId, "TRACK_EXCEPTION", TrackExceptionResponse(context.server, track, exception))
        WebSockets.sendText(JsonStream.serialize(response), context.channel, null)
    }

    /**
     * Fired by Lavaplayer when the track that was playing got stuck.
     *
     * This sends a `TRACK_STUCK` event, **wrapped in a Dispatch Response**, across a WebSocketChannel.
     *
     * @param player The AudioPlayer object itself.
     * @param track The AudioTrack that got stuck.
     * @param thresholdMs The threshold the track was/is stuck for.
     */
    override fun onTrackStuck(player: AudioPlayer, track: AudioTrack, thresholdMs: Long) {
        val response = DispatchResponse(context, guildId, "TRACK_STUCK", TrackStuckResponse(context.server, track, thresholdMs))
        WebSockets.sendText(JsonStream.serialize(response), context.channel, null)
    }

    /**
     * Fired by Lavaplayer when the AudioPlayer is paused.
     *
     * This sends a `PLAYER_PAUSE` event, **wrapped in a Dispatch Response**, across a WebSocketChannel.
     * **Note: This sends the same event as when the player is resumed, but the data is set to `true` (for "is paused").**
     *
     * @param player The AudioPlayer object itself.
     */
    override fun onPlayerPause(player: AudioPlayer) {
        val response = DispatchResponse(context, guildId, "PLAYER_PAUSE", true)
        WebSockets.sendText(JsonStream.serialize(response), context.channel, null)
    }

    /**
     * Fired by Lavaplayer when the AudioPlayer is resumed.
     *
     * This sends a `PLAYER_PAUSE` event, **wrapped in a Dispatch Response**, across a WebSocketChannel.
     * **Note: This sends the same event as when the player is paused, but the data is set to `false` (for "is paused").**
     * @param player The AudioPlayer object itself.
     */
    override fun onPlayerResume(player: AudioPlayer) {
        val response = DispatchResponse(context, guildId, "PLAYER_PAUSE", false)
        WebSockets.sendText(JsonStream.serialize(response), context.channel, null)
    }
}