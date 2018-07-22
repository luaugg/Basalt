package basalt.messages.client

import com.jsoniter.annotation.JsonCreator
import com.jsoniter.annotation.JsonProperty

@Suppress("UNUSED")
class InitializeRequest @JsonCreator constructor(@JsonProperty("op", required = true, nullable = false) val op: String,
                                                 @JsonProperty("guild_id", required = true, nullable = false) val guildId: String,
                                                 @JsonProperty("sessionId", required = true, nullable = false) val sessionid: String,
                                                 @JsonProperty("token", required = true, nullable = false) val token: String,
                                                 @JsonProperty("endpoint", required = true, nullable = false) val endpoint: String)