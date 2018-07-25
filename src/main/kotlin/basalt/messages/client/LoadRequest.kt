package basalt.messages.client

import com.jsoniter.annotation.JsonCreator
import com.jsoniter.annotation.JsonProperty

@Suppress("UNUSED")
class LoadRequest @JsonCreator constructor(@JsonProperty("op", required = true, nullable = false) val op: String,
                                           @JsonProperty("identifiers", required = true, nullable = false) val identifiers: Array<String>)