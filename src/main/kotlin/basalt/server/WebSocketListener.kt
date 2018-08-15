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
package basalt.server

import basalt.messages.client.*
import basalt.messages.server.DispatchResponse
import basalt.messages.server.LoadTrackResponse
import basalt.messages.server.PlayerUpdate
import basalt.player.AudioLoadHandler
import basalt.player.BasaltPlayer
import basalt.server.ErrorResponses.*
import com.jsoniter.JsonIterator
import com.jsoniter.output.JsonStream
import io.undertow.websockets.core.*
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import kotlinx.coroutines.experimental.launch
import org.slf4j.LoggerFactory
import space.npstr.magma.MagmaMember
import space.npstr.magma.MagmaServerUpdate
import java.nio.charset.Charset
import java.util.*
import kotlin.math.min

/**
 * The listener class which responds to WebSocket Events, including (but not limited to) incoming messages.
 *
 * @property server A [BasaltServer] reference used mainly to access information.
 *
 * @author Sam Pritchard
 * @since 1.0
 * @constructor Constructs a WebSocketListener from a [BasaltServer] instance.
 */

class WebSocketListener internal constructor(private val server: BasaltServer): AbstractReceiveListener() {
	/**
	 * Fired by Undertow when the WebSocket Connection between Basalt and a client closes.
	 * @param webSocketChannel The WebSocketChannel that was closed.
	 * @param channel The StreamSourceFrameChannel that was closed.
	 */
	override fun onClose(webSocketChannel: WebSocketChannel, channel: StreamSourceFrameChannel) {
		super.onClose(webSocketChannel, channel)
        server.contexts.remove(webSocketChannel)
		if (webSocketChannel.isCloseInitiatedByRemotePeer) {
			val host = webSocketChannel.sourceAddress
					.toString()
					.replaceFirst("/", "")
					.replaceFirst("0:0:0:0:0:0:0:1", "localhost")
			LOGGER.info("Connection closed from {}", host)
		}
	}

	/**
	 * Fired by Undertow upon an error that was thrown during WebSocket Communication.
	 * @param channel The WebSocketChannel that errored.
	 * @param error The Throwable instance that was thrown.
	 */
	override fun onError(channel: WebSocketChannel, error: Throwable) {
		super.onError(channel, error)
		LOGGER.error("Error during WebSocket Communication!", error)
	}

	/**
	 * Fired by Undertow upon a full text message (such as JSON Data) being sent by a client.
	 * @param channel The WebSocketChannel that data was sent to.
	 * @param message The BufferedTextMessage data that was sent.
	 */
	override fun onFullTextMessage(channel: WebSocketChannel, message: BufferedTextMessage) {
		val raw = String(message.data.toByteArray(Charset.forName("UTF-8")))
        LOGGER.info("Received Message: {}", raw)
		try {
			val data = JsonIterator.deserialize(raw)
			when (data["op"]!!.toString()) {
				"initialize" -> {
					val init = JsonIterator.deserialize(raw, InitializeRequest::class.java)
					val context = server.contexts[channel]
					if (context == null) {
						LOGGER.error("SocketContext is null for Guild ID: {}\nThis should never happen!", init.guildId)
						return
					}
					if (context.players[init.guildId] != null) {
						LOGGER.warn("Player already initialized for User ID: {} and Guild ID: {}", context.userId, init.guildId)
						val response = DispatchResponse(init.key, init.guildId, "ERROR", PLAYER_ALREADY_INITIALIZED.name)
						WebSockets.sendText(JsonStream.serialize(response), channel, null)
						return
					}
					val member = MagmaMember.builder()
							.guildId(init.guildId)
							.userId(context.userId)
							.build()
					val update = MagmaServerUpdate.builder()
							.sessionId(init.sessionId)
							.endpoint(init.endpoint)
							.token(init.token)
							.build()
					val basalt = BasaltPlayer(context, init.guildId, server.sourceManager.createPlayer())
					context.players[init.guildId] = basalt
					server.magma.provideVoiceServerUpdate(member, update)
					val response = DispatchResponse(init.key, init.guildId, "INITIALIZED")
					WebSockets.sendText(JsonStream.serialize(response), channel, null)
					LOGGER.info("Initialized connection from User ID: {} and Guild ID: {}", member.userId, member.guildId)
				}
				"play" -> {
					val play = JsonIterator.deserialize(raw, PlayRequest::class.java)
					val context = server.contexts[channel]
					val guildId = play.guildId
					if (context == null) {
						LOGGER.error("SocketContext is null for this WebSocketChannel (Guild ID: {})!", guildId)
						val response = DispatchResponse(play.key, guildId, "ERROR", SOCKET_CONTEXT_NULL.name)
						WebSockets.sendText(JsonStream.serialize(response), channel, null)
						return
					}
					val player = context.players[guildId]
					if (player == null) {
						LOGGER.warn("Player is null for Guild ID: {} and User ID: {} (Try initializing first!)", guildId, context.userId)
						val response = DispatchResponse(play.key, guildId, "ERROR", PLAYER_NOT_INITIALIZED.name)
						WebSockets.sendText(JsonStream.serialize(response), channel, null)
						return
					}
                    val member = MagmaMember.builder()
                            .guildId(play.guildId)
                            .userId(context.userId)
                            .build()
                    if (player.audioSender == null)
                        player.audioSender = AudioSender(player.audioPlayer)
                    server.magma.setSendHandler(member, player.audioSender)
					val track = server.trackUtil.toAudioTrack(play.track)
					if (play.startTime != null)
						track.position = play.startTime
					player.startKeys.add(play.key)
					player.audioPlayer.playTrack(server.trackUtil.toAudioTrack(play.track))
                    LOGGER.debug("Playing track: {} for Guild ID: {} and User ID: {}", track.info.title, play.guildId, context.userId)
				}
				"setPaused" -> {
					val pause = JsonIterator.deserialize(raw, SetPausedRequest::class.java)
					val context = server.contexts[channel]
					val guildId = pause.guildId
					if (context == null) {
						LOGGER.error("SocketContext is null for this WebSocketChannel (Guild ID: {})!", guildId)
						val response = DispatchResponse(pause.key, guildId, "ERROR", SOCKET_CONTEXT_NULL.name)
						WebSockets.sendText(JsonStream.serialize(response), channel, null)
						return
					}
					val player = context.players[guildId]
					if (player == null) {
						LOGGER.warn("Player is null for Guild ID: {} and User ID: {} (Try initializing first!)", guildId, context.userId)
						val response = DispatchResponse(pause.key, guildId, "ERROR", PLAYER_NOT_INITIALIZED.name)
						WebSockets.sendText(JsonStream.serialize(response), channel, null)
						return
					}
					val paused = pause.paused
					if (player.audioPlayer.isPaused == paused) {
						val enum: String?
						val msg: String?
						if (paused) {
							enum = PLAYER_ALREADY_PAUSED.name
							msg = "Player is already paused for"
						}
						else {
							enum = PLAYER_ALREADY_RESUMED.name
							msg = "Player has already been resumed for"
						}
						LOGGER.warn("{} Guild ID: {} and User ID: {}", msg, guildId, context.userId)
						val response = DispatchResponse(pause.key, guildId, "ERROR", enum)
						WebSockets.sendText(JsonStream.serialize(response), channel, null)
						return
					}
					player.pauseKey = pause.key
					player.audioPlayer.isPaused = pause.paused
                    LOGGER.debug("Set paused to {} for Guild ID: {} and User ID: {}", pause.paused, pause.guildId, context.userId)
				}
				"stop" -> {
					val stop = JsonIterator.deserialize(raw, EmptyRequestBody::class.java)
					val context = server.contexts[channel]
					val guildId = stop.guildId
					if (context == null) {
						LOGGER.error("SocketContext is null for this WebSocketChannel (Guild ID: {})!", guildId)
						val response = DispatchResponse(stop.key, guildId, "ERROR", SOCKET_CONTEXT_NULL.name)
						WebSockets.sendText(JsonStream.serialize(response), channel, null)
						return
					}
					val player = context.players[guildId]
					if (player == null) {
						LOGGER.warn("Player is null for Guild ID: {} and User ID: {} (Try initializing first!)", guildId, context.userId)
						val response = DispatchResponse(stop.key, guildId, "ERROR", PLAYER_NOT_INITIALIZED.name)
						WebSockets.sendText(JsonStream.serialize(response), channel, null)
						return
					}
					if (player.audioPlayer.playingTrack == null) {
						LOGGER.warn("Track is null for Guild ID: {} and User ID: {}", guildId, context.userId)
						val response = DispatchResponse(stop.key, guildId, "ERROR", NO_TRACK.name)
						WebSockets.sendText(JsonStream.serialize(response), channel, null)
						return
					}
					player.stopKey = stop.key
					player.audioPlayer.stopTrack()
                    LOGGER.debug("Stopped track for Guild ID: {} and User ID: {}", stop.guildId, context.userId)
				}
				"destroy" -> {
					val destroy = JsonIterator.deserialize(raw, EmptyRequestBody::class.java)
					val context = server.contexts[channel]
					val guildId = destroy.guildId
					if (context == null) {
						LOGGER.error("SocketContext is null for this WebSocketChannel (Guild ID: {})!", guildId)
						val response = DispatchResponse(destroy.key, guildId, "ERROR", SOCKET_CONTEXT_NULL.name)
						WebSockets.sendText(JsonStream.serialize(response), channel, null)
						return
					}
					val player = context.players[guildId]
					if (player == null) {
						LOGGER.warn("Player is null for Guild ID: {} and User ID: {} (Try initializing first!)", guildId, context.userId)
						val response = DispatchResponse(destroy.key, guildId, "ERROR", PLAYER_NOT_INITIALIZED.name)
						WebSockets.sendText(JsonStream.serialize(response), channel, null)
						return
					}
					player.audioPlayer.destroy()
					player.context.players.remove(destroy.guildId)
					val member = MagmaMember.builder()
							.guildId(destroy.guildId)
							.userId(player.context.userId)
							.build()
					server.magma.removeSendHandler(member)
					server.magma.closeConnection(member)
					val response = DispatchResponse(destroy.key, destroy.guildId, "DESTROYED")
					WebSockets.sendText(JsonStream.serialize(response), channel, null)
                    LOGGER.debug("Destroyed player for Guild ID: {} and User ID: {}", destroy.guildId, context.userId)
				}
				"volume" -> {
					val volume = JsonIterator.deserialize(raw, SetVolumeRequest::class.java)
					val context = server.contexts[channel]
					val guildId = volume.guildId
					if (context == null) {
						LOGGER.error("SocketContext is null for this WebSocketChannel (Guild ID: {})!", guildId)
						val response = DispatchResponse(volume.key, guildId, "ERROR", SOCKET_CONTEXT_NULL.name)
						WebSockets.sendText(JsonStream.serialize(response), channel, null)
						return
					}
					val player = context.players[guildId]
					if (player == null) {
						LOGGER.warn("Player is null for Guild ID: {} and User ID: {} (Try initializing first!)", guildId, context.userId)
						val response = DispatchResponse(volume.key, guildId, "ERROR", PLAYER_NOT_INITIALIZED.name)
						WebSockets.sendText(JsonStream.serialize(response), channel, null)
						return
					}
					if (volume.volume < 0 || volume.volume > 1000) {
						LOGGER.warn("Volume cannot be negative or above 1000 for User ID: {} and Guild ID: {}",
								player.context.userId, volume.guildId)
						val response = DispatchResponse(volume.key, guildId, "ERROR", VOLUME_OUT_OF_BOUNDS.name)
						WebSockets.sendText(JsonStream.serialize(response), channel, null)
						return
					}
					player.audioPlayer.volume = volume.volume
					val response = DispatchResponse(volume.key, volume.guildId, "VOLUME_UPDATE", volume.volume)
					WebSockets.sendText(JsonStream.serialize(response), channel, null)
                    LOGGER.debug("Set volume to {} for Guild ID: {} and User ID: {}", volume.volume, volume.guildId, context.userId)
				}
				"seek" -> {
					val seek = JsonIterator.deserialize(raw, SeekRequest::class.java)
					val context = server.contexts[channel]
					val guildId = seek.guildId
					if (context == null) {
						LOGGER.error("SocketContext is null for this WebSocketChannel (Guild ID: {})!", guildId)
						val response = DispatchResponse(seek.key, guildId, "ERROR", SOCKET_CONTEXT_NULL.name)
						WebSockets.sendText(JsonStream.serialize(response), channel, null)
						return
					}
					val player = context.players[guildId]
					if (player == null) {
						LOGGER.warn("Player is null for Guild ID: {} and User ID: {} (Try initializing first!)", guildId, context.userId)
						val response = DispatchResponse(seek.key, guildId, "ERROR", PLAYER_NOT_INITIALIZED.name)
						WebSockets.sendText(JsonStream.serialize(response), channel, null)
						return
					}
					val track = player.audioPlayer.playingTrack
					if (track == null) {
						LOGGER.warn("Track is null for Guild ID: {} and User ID: {}", seek.guildId, context.userId)
						val response = DispatchResponse(seek.key, guildId, "ERROR", NO_TRACK.name)
						WebSockets.sendText(JsonStream.serialize(response), channel, null)
						return
					}
					if (!track.isSeekable) {
						LOGGER.warn("Track is not seekable for Guild ID: {} and User ID: {}", seek.guildId, context.userId)
						val response = DispatchResponse(seek.key, guildId, "ERROR", TRACK_NOT_SEEKABLE.name)
						WebSockets.sendText(JsonStream.serialize(response), channel, null)
						return
					}
					if (seek.position < 0 || seek.position > track.duration) {
						LOGGER.warn("Seek position cannot be negative or larger than the duration of the track! Guild ID: {} and User ID: {}",
								seek.guildId, context.userId)
						val response = DispatchResponse(seek.key, guildId, "ERROR", POSITION_OUT_OF_BOUNDS.name)
						WebSockets.sendText(JsonStream.serialize(response), channel, null)
						return
					}
					player.audioPlayer.playingTrack.position = seek.position
					val response = DispatchResponse(seek.key, seek.guildId, "POSITION_UPDATE", seek.position)
					WebSockets.sendText(JsonStream.serialize(response), channel, null)
                    LOGGER.debug("Set position of track to {} for Guild ID: {} and User ID: {}", seek.position, guildId, context.userId)
				}
				"loadIdentifiers" -> {
					val load = JsonIterator.deserialize(raw, LoadRequest::class.java)
					val context = server.contexts[channel]
					if (context == null) {
						LOGGER.error("SocketContext is null. This should *never* happen.")
						val response = DispatchResponse(load.key, null, "ERROR", SOCKET_CONTEXT_NULL.name)
						WebSockets.sendText(JsonStream.serialize(response), channel, null)
						return
					}
					val chunkSize = server.loadChunkSize
					val identifiers = LinkedList<String>()
					identifiers.addAll(load.identifiers)
					val chunks = Math.ceil((identifiers.size.toDouble() / chunkSize.toDouble())).toInt()
                    LOGGER.debug("Loaded {} identifiers for User ID: {}", identifiers.size, context.userId)
					launch {
						for (i in 1..chunks) {
							val list = ObjectArrayList<LoadTrackResponse>(chunkSize)
							val size = min(identifiers.size, chunkSize)
							for (index in 1..size) {
								AudioLoadHandler(server).load(identifiers.poll())
										.thenApply { LoadTrackResponse(it) }
										.thenAccept { list.add(it) }
										.thenAccept {
											if (index == size) {
												val response = DispatchResponse(load.key, null, "LOAD_IDENTIFIERS_CHUNK", list.toTypedArray())
												WebSockets.sendText(JsonStream.serialize(response), channel, null)
											}
											if (i == chunks) {
												val response = DispatchResponse(load.key, null, "CHUNKS_FINISHED")
												WebSockets.sendText(JsonStream.serialize(response), channel, null)
											}
										}
							}
						}
					}
				}
			}
		} catch (err: Throwable) {
			LOGGER.error("Error when responding to text message!", err)
		}
	}

    /**
	 * @suppress
	 */
	companion object {
		/**
		 * @suppress
		 */
		private val LOGGER = LoggerFactory.getLogger(WebSocketListener::class.java)
	}
}