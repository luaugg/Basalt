package basalt.server

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame
import net.dv8tion.jda.core.audio.AudioSendHandler

class AudioSender internal constructor(private val player: AudioPlayer): AudioSendHandler {
    @Volatile private var data: AudioFrame? = null
    override fun canProvide(): Boolean {
        data = player.provide()
        return data != null
    }
    override fun provide20MsAudio(): ByteArray = data!!.data
    override fun isOpus(): Boolean = true
}