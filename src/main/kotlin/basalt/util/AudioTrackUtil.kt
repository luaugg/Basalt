package basalt.util

import basalt.server.BasaltServer
import com.sedmelluq.discord.lavaplayer.tools.io.MessageInput
import com.sedmelluq.discord.lavaplayer.tools.io.MessageOutput
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import it.unimi.dsi.fastutil.io.FastByteArrayInputStream
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream
import net.iharder.Base64
import org.slf4j.LoggerFactory
import java.io.IOException

class AudioTrackUtil internal constructor(private val server: BasaltServer) {
    fun fromAudioTrack(track: AudioTrack): String {
        try {
            val out = FastByteArrayOutputStream()
            server.sourceManager.encodeTrack(MessageOutput(out), track)
            return Base64.encodeBytes(out.array)
        } catch (err: IOException) {
            LOGGER.error("Error when encoding AudioTrack!", err)
            throw err
        }
    }
    fun toAudioTrack(data: String): AudioTrack {
        try {
            return server.sourceManager.decodeTrack(MessageInput(FastByteArrayInputStream(Base64.decode(data)))).decodedTrack
        } catch (err: IOException) {
            LOGGER.error("Error when decoding AudioTrack data!", err)
            throw err
        }
    }
    companion object {
        private val LOGGER = LoggerFactory.getLogger(AudioTrackUtil::class.java)
    }
}