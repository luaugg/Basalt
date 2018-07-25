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

import basalt.messages.server.TrackPair
import org.slf4j.LoggerFactory

/**
 * A result class which represents a singular, successful load result.
 *
 * @property playlistInfo **nullable** pair of information (playlist name + index of selected track)
 * @property tracks an array of TrackPairs
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