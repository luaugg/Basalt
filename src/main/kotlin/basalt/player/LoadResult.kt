package basalt.player

import basalt.messages.server.TrackPair
import org.slf4j.LoggerFactory

/**
 * A result class which represents a singular, successful load result.
 *
 * @property playlistInfo **nullable** pair of information (playlist name + index of selected track)
 * @property tracks an array of [TrackPair]s
 * @property result a [ResultStatus] that can never be unknown
 *
 * @author Sam Pritchard
 * @since 1.0
 * @constructor Constructs a LoadResult from **possibly-null** playlist info, a track array and a [ResultStatus].
 */

@Suppress("UNUSED")
class LoadResult internal constructor(internal val playlistInfo: Pair<String, Int>?,
                                      internal val tracks: Array<TrackPair>, internal val result: ResultStatus) {
    init {
        if (result === ResultStatus.UNKNOWN) {
            LOGGER.error("Result Status is UNKNOWN!")
            throw IllegalArgumentException("ResultStatus should never be equal to UNKNOWN!")
        }
    }

    /**
     * @suppress
     */
    companion object {
        /**
         * @suppress
         */
        private val LOGGER = LoggerFactory.getLogger(LoadResult::class.java)
    }
}