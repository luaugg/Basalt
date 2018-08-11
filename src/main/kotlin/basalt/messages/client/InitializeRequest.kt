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
package basalt.messages.client

import com.jsoniter.annotation.JsonCreator
import com.jsoniter.annotation.JsonProperty

@Suppress("UNUSED")
class InitializeRequest @JsonCreator constructor(@JsonProperty("key", required = true, nullable = false) val key: String,
                                                 @JsonProperty("op", required = true, nullable = false) val op: String,
                                                 @JsonProperty("guildId", required = true, nullable = false) val guildId: String,
                                                 @JsonProperty("sessionId", required = true, nullable = false) val sessionId: String,
                                                 @JsonProperty("token", required = true, nullable = false) val token: String,
                                                 @JsonProperty("endpoint", required = true, nullable = false) val endpoint: String)