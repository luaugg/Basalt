package basalt.player

import basalt.server.messages.TrackPair
import org.slf4j.LoggerFactory

@Suppress("UNUSED")
class LoadResult(val playlistInfo: Pair<String, Int>?, val tracks: Array<TrackPair>, val result: ResultStatus) {
    init {
        if (result === ResultStatus.UNKNOWN)
            LOGGER.error("Result Status is UNKNOWN!")
    }
    companion object {
        private val LOGGER = LoggerFactory.getLogger(LoadResult::class.java)
    }
}