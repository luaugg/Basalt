package basalt.player

import com.github.shredder121.asyncaudio.jda.AsyncPacketProviderFactory
import com.sedmelluq.discord.lavaplayer.jdaudp.NativeAudioSendFactory
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import org.slf4j.LoggerFactory
import space.npstr.magma.MagmaApi

class BasaltPlayer(val userId: String): AudioEventAdapter() {

    override fun onTrackStart(player: AudioPlayer?, track: AudioTrack?) {
        super.onTrackStart(player, track)
    }
    override fun onTrackEnd(player: AudioPlayer?, track: AudioTrack?, endReason: AudioTrackEndReason?) {
        super.onTrackEnd(player, track, endReason)
    }
    override fun onTrackException(player: AudioPlayer?, track: AudioTrack?, exception: FriendlyException?) {
        super.onTrackException(player, track, exception)
    }
    override fun onTrackStuck(player: AudioPlayer?, track: AudioTrack?, thresholdMs: Long) {
        super.onTrackStuck(player, track, thresholdMs)
    }
    override fun onPlayerPause(player: AudioPlayer?) {
        super.onPlayerPause(player)
    }
    override fun onPlayerResume(player: AudioPlayer?) {
        super.onPlayerResume(player)
    }
    companion object {
        private val logger = LoggerFactory.getLogger(BasaltPlayer::class.java)
    }
}