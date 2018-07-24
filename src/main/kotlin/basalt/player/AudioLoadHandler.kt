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

class AudioLoadHandler internal constructor(private val server: BasaltServer): AudioLoadResultHandler {
    private val future = CompletableFuture<LoadResult>()
    private val isUsed = AtomicBoolean(false)
    fun load(identifier: String): CompletionStage<LoadResult> {
        if (isUsed.getAndSet(true)) {
            LOGGER.error("AudioLoadHandler can only be used once!")
            throw IllegalStateException("AudioLoadHandler can only be used once!")
        }
        server.sourceManager.loadItem(identifier, this)
        return future
    }
    override fun trackLoaded(track: AudioTrack) {
        future.complete(LoadResult(null, arrayOf(TrackPair(server.trackUtil.fromAudioTrack(track), track)), ResultStatus.TRACK_LOADED))
    }
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
    override fun noMatches() {
        future.complete(NO_MATCHES)
    }
    override fun loadFailed(exception: FriendlyException) {
        LOGGER.error("Load failed!", exception)
        future.complete(LOAD_FAILED)
    }
    companion object {
        private val LOGGER = LoggerFactory.getLogger(AudioLoadHandler::class.java)
        private val NO_MATCHES = LoadResult(null, emptyArray(), ResultStatus.NO_MATCHES)
        private val LOAD_FAILED = LoadResult(null, emptyArray(), ResultStatus.LOAD_FAILED)
    }
}