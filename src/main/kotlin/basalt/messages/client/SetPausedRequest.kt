package basalt.messages.client

import com.jsoniter.annotation.JsonCreator
import com.jsoniter.annotation.JsonProperty

@Suppress("UNUSED")
class SetPausedRequest @JsonCreator constructor(@JsonProperty("op", required = true, nullable = false) val op: String,
                                                @JsonProperty("guildId", required = true, nullable = false) val guildId: String,
                                                @JsonProperty("paused", required = true, nullable = false) val paused: Boolean)