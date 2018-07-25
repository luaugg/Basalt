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
package basalt.messages.server

import basalt.player.SocketContext
import com.jsoniter.annotation.JsonIgnore

@Suppress("UNUSED")
class DispatchResponse internal constructor(@field:JsonIgnore private val context: SocketContext,
                                            val guildId: String? = null, val name: String, val data: Any? = null) {
    val op = "dispatch"
    val seq = context.seq.get()
}