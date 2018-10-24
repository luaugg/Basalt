package basalt.server

/**
 * An inline class which represents the type of message response sent back to clients.
 * Inline classes are optimized heavily by the Kotlin compiler, so no performance hits occur.
 *
 * @property type The type of message.
 * @since 4.0.0
 * @author Sam Pritchard
 */
inline class MessageType(val type: String)

/**
 * An object containing a bunch of message types that indicate the type of event being sent back to clients.
 *
 * @author Sam Pritchard
 * @since 4.0.0
 */

object MessageTypes {
    /** Indicates an error occurred. List of *most* errors can be found in the docs for [ErrorResponses]. */
    val ERROR = MessageType("ERROR")

    /** Indicates a player was successfully initialized. */
    val INITIALIZED = MessageType("INITIALIZED")

    /** Indicates a player was successfully destroyed. */
    val DESTROYED = MessageType("DESTROYED")

    /** Indicates a player's volume was successfully set. */
    val VOLUME_UPDATE = MessageType("VOLUME_UPDATE")

    /** Indicates a player's position was successfully set. */
    val POSITION_UPDATE = MessageType("POSITION_UPDATE")

    /** Indicates a track load chunk (only sent when requesting tracks via WebSocket) */
    val LOAD_IDENTIFIERS_CHUNK = MessageType("LOAD_IDENTIFIERS_CHUNK")

    /** Indicates that the chunks finished for the requested identifier (not specified). */
    val CHUNKS_FINISHED = MessageType("CHUNKS_FINISHED")

    /** Indicates that a track started. */
    val TRACK_START = MessageType("TRACK_START")

    /** Indicates that a track ended. */
    val TRACK_END = MessageType("TRACK_END")

    /** Indicates that an exception was thrown within track playback. */
    val TRACK_EXCEPTION = MessageType("TRACK_EXCEPTION")

    /** Indicates that a track got stuck before it could be finished. */
    val TRACK_STUCK = MessageType("TRACK_STUCK")

    /** Indicates that a player was paused or resumed. */
    val PLAYER_PAUSE = MessageType("PLAYER_PAUSE")


}