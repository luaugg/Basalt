package basalt.server

import basalt.player.BasaltPlayer
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame
import net.dv8tion.jda.core.audio.AudioSendHandler

class AudioSender(val player: BasaltPlayer): AudioSendHandler {
    private var frame: AudioFrame? = null

    override fun provide20MsAudio() = frame?.data
    override fun canProvide() = frame?.let { frame = player.player.provide(); frame != null } == true
    override fun isOpus() = true
}