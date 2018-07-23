package basalt.server

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame
import net.dv8tion.jda.core.audio.AudioSendHandler

/**
 * Sender class which takes in Opus frames from Lavaplayer and passes them to Magma, where they are then
 * subsequently sent to Discord.
 *
 * @constructor Constructs an AudioSender instance from an AudioPlayer.
 * @author Sam Pritchard
 * @since 1.0
 */

class AudioSender internal constructor(private val player: AudioPlayer): AudioSendHandler {
    /**
     * @suppress
     */
    @Volatile private var data: AudioFrame? = null

    /**
     * Grabs **possibly-null** provided data from the AudioPlayer instance, sets it and returns whether it's `null` or not.
     * @return Whether or not the provided data was `null`.
     */
    override fun canProvide(): Boolean {
        data = player.provide()
        return data != null
    }

    /**
     * Returns the raw array of bytes, representing 20 milliseconds of audio data, as set by the [canProvide] method.
     * @return 20 milliseconds of raw byte data.
     */
    override fun provide20MsAudio(): ByteArray = data!!.data

    /**
     * Returns whether or not this send handler provides Opus-encoded data, which is automatically, and constantly, true
     * as Lavaplayer already converts the data.
     * @return true
     */
    override fun isOpus(): Boolean = true
}