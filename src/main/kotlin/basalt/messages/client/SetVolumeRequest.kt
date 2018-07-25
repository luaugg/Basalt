package basalt.messages.client

import com.jsoniter.annotation.JsonCreator
import com.jsoniter.annotation.JsonProperty

@Suppress("UNUSED")
class SetVolumeRequest @JsonCreator constructor(@JsonProperty("op", required = true, nullable = false) val op: String,
                                                @JsonProperty("guildId", required = true, nullable = false) val guildId: String,
                                                @JsonProperty("volume", required = true, nullable = false) val volume: Int)