package basalt.messages.client

import com.jsoniter.annotation.JsonCreator
import com.jsoniter.annotation.JsonProperty

@Suppress("UNUSED")
class PlayEvent @JsonCreator constructor(@JsonProperty("op", required = true, nullable = false) val op: String,
                                         @JsonProperty("guildId", required = true, nullable = false) val guildId: String,
                                         @JsonProperty("track", required = true, nullable = false) val track: String,
                                         @JsonProperty("startTime") val startTime: Long?,
                                         @JsonProperty("endTime") val endTime: Long?)