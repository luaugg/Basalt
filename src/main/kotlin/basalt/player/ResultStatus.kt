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

/**
 * Enum which corresponds to how a source loaded.
 *
 * For example, if you load a single track successfully the returned "load type" will be equal to `TRACK_LOADED`.
 *
 * @author Sam Pritchard
 * @since 1.0
 */
enum class ResultStatus {
    /** Returned when a single AudioTrack is loaded. */
    TRACK_LOADED,
    /** Returned when a playlist of AudioTracks is loaded. */
    PLAYLIST_LOADED,
    /** Returned when a search result is loaded. */
    SEARCH_RESULT,
    /** Returned if no sources could be attributed to the provided identifier. */
    NO_MATCHES,
    /** Returned if Lavaplayer failed to load the source. */
    LOAD_FAILED,
    /** Usually never returned. If this is, a report should be made to the developers. */
    UNKNOWN
}