package basalt.player

import basalt.server.BasaltServer
import basalt.messages.server.TrackPair
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean

// Credit to Napster (@npstr) for the original Java Completion Code.

/**
 * Class which loads sources based on provided identifiers asynchronously.
 *
 * Each instance of this class can only be used once, and an exception will be thrown as well as an error log
 * being produced each time this is used more than once -- which it is not, if using the normal, stable Basalt release.
 *
 * @property server A [BasaltServer] reference.
 * @property future A CompletableFuture that can be completed with a [LoadResult].
 * @property isUsed An AtomicBoolean that atomically checks to see if this instance has already been used before.
 *
 * @author Sam Pritchard
 * @since 1.0
 * @constructor Constructs an AudioLoadHandler from a [BasaltServer] instance.
 */
class AudioLoadHandler internal constructor(private val server: BasaltServer): AudioLoadResultHandler {
    private val future = CompletableFuture<LoadResult>()
    private val isUsed = AtomicBoolean(false)

    /**
     * Loads a source based on a given identifier, returning a CompletionStage that is used internally for various purposes.
     *
     * @param identifier The identifier to load.
     * @return A CompletionStage which eventually completes with a [LoadResult].
     */
    fun load(identifier: String): CompletionStage<LoadResult> {
        if (isUsed.getAndSet(true)) {
            LOGGER.error("AudioLoadHandler can only be used once!")
            throw IllegalStateException("AudioLoadHandler can only be used once!")
        }
        server.sourceManager.loadItem(identifier, this)
        return future
    }

    /**
     * Called by Lavaplayer whenever a single track is loaded successfully, completing the result.
     * @param track The AudioTrack object.
     */
    override fun trackLoaded(track: AudioTrack) {
        future.complete(LoadResult(null, arrayOf(TrackPair(server.trackUtil.fromAudioTrack(track), track)), ResultStatus.TRACK_LOADED))
    }

    /**
     * Called by Lavaplayer whenever a playlist of tracks (including search results) is loaded successfully, completing the result.
     * @param playlist The AudioPlaylist object.
     */
    override fun playlistLoaded(playlist: AudioPlaylist) {
        val (name, selected, type) = if (playlist.isSearchResult)
            Triple(null, -1, ResultStatus.SEARCH_RESULT)
        else
            Triple(playlist.name, playlist.tracks.indexOf(playlist.selectedTrack), ResultStatus.PLAYLIST_LOADED)
        future.complete(LoadResult(if (type === ResultStatus.PLAYLIST_LOADED)
            Pair(name!!, selected)
        else
            null, Array(playlist.tracks.size) {
            val track = playlist.tracks[it]
            TrackPair(server.trackUtil.fromAudioTrack(track), track)
        }, type))
    }

    /**
     * Called by Lavaplayer if no matches could be associated with the provided identifier, completing the result.
     */
    override fun noMatches() {
        future.complete(NO_MATCHES)
    }

    /**
     * Called by Lavaplayer whenever an error occurs while loading a source, completing the result.
     * @param exception The FriendlyException that was thrown.
     */
    override fun loadFailed(exception: FriendlyException) {
        LOGGER.error("Load failed!", exception)
        future.complete(LOAD_FAILED)
    }

    /**
     * @suppress
     */
    companion object {
        /**
         * @suppress
         */
        private val LOGGER = LoggerFactory.getLogger(AudioLoadHandler::class.java)
        /**
         * @suppress
         */
        private val NO_MATCHES = LoadResult(null, emptyArray(), ResultStatus.NO_MATCHES)
        /**
         * @suppress
         */
        private val LOAD_FAILED = LoadResult(null, emptyArray(), ResultStatus.LOAD_FAILED)
    }
}