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