package basalt.server

/**
 * An enum of responses that are returned in Dispatched `ERROR` Responses, as they're written here.
 *
 * This means that, for example, if you tried to set the volume of a player to -5, an `ERROR` event would be
 * dispatched (as in wrapped in a Dispatch Response and sent back) in response with the value being
 * "VOLUME_OUT_OF_BOUNDS".
 *
 * @author Sam Pritchard
 * @since 1.1.0
 */

enum class ErrorResponses {
    /** Returned if no track was playing upon an attempt to update it (i.e seeking). */
    NO_TRACK,
    /** Returned if the currently playing track isn't seekable upon attempting to seek. */
    TRACK_NOT_SEEKABLE,
    /** Returned if an attempt was made to seek to a position below 0 or longer than the duration of the track. */
    POSITION_OUT_OF_BOUNDS,
    /** Returned if an attempt was made to set the volume to below 0 or above 1000. */
    VOLUME_OUT_OF_BOUNDS,
    /** Returned if an attempt was made to update a non-existent player (before initializing). */
    PLAYER_NOT_INITIALIZED,
    /** Returned if an attempt was made to initialize an already-initialized player. */
    PLAYER_ALREADY_INITIALIZED
}