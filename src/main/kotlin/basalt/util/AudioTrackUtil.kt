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

/**
 * The utility class which converts AudioTracks to easily-transportable data and vice versa.
 *
 * @property server A [BasaltServer] reference, used to access the Source Manager.
 *
 * @constructor Constructs a new AudioTrackUtil class with an attached [BasaltServer].
 * @author Sam Pritchard
 * @since 0.1
 */
class AudioTrackUtil internal constructor(private val server: BasaltServer) {
    /**
     * Converts a **not-null** AudioTrack into its data representation, which is then used for various purposes.
     *
     * <br><p>The data representation is used mainly by clients in order to play media sources, as passing/storing
     * an entire JSON Object is a little harder and less performant.
     *
     * <br>Additionally, Basalt makes no requests in order to resolve the track data as it already has been resolved.
     * When converting strings via the [toAudioTrack][toAudioTrack] method, similarly, Basalt doesn't have
     * to make requests in order to decode the track as the data already contains all the information.</p>
     *
     * @param track The track object to be encoded.
     * @return The encoded data of the track.
     */
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

    /**
     * Converts a **not-null** data representation of an AudioTrack into an AudioTrack, which is then used for various purposes.
     *
     * <br><p>This track is only really used internally by Basalt in order to play the raw track
     * itself (as opposed to the data representation which is used mainly to transport a track object over the net).
     *
     * <br>Additionally, and similarly to the [fromAudioTrack][fromAudioTrack] method, Basalt does not have to make any requests to resolve
     * the track data as it is already contained inside the string.</p>
     *
     * @param data The encoded track data, to be decoded into an AudioTrack.
     * @return The track object represented in the encoded data.
     */
    fun toAudioTrack(data: String): AudioTrack {
        try {
            return server.sourceManager.decodeTrack(MessageInput(FastByteArrayInputStream(Base64.decode(data)))).decodedTrack
        } catch (err: IOException) {
            LOGGER.error("Error when decoding AudioTrack data!", err)
            throw err
        }
    }

    /**
     * @suppress
     */
    companion object {
        /**
         * @suppress
         */
        private val LOGGER = LoggerFactory.getLogger(AudioTrackUtil::class.java)
    }
}