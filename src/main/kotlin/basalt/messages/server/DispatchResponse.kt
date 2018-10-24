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

import com.jsoniter.annotation.JsonIgnore
import com.jsoniter.annotation.JsonUnwrapper
import com.jsoniter.output.JsonStream

@Suppress("UNUSED")
class DispatchResponse internal constructor(@field:JsonIgnore val guildId: String? = null,
                                            @field:JsonIgnore val name: String,
                                            @field:JsonIgnore val data: Any? = null) {
    @JsonUnwrapper
    fun unwrapData(stream: JsonStream) {
        with (stream) {
            writeObjectField("op")
            writeVal("dispatch")
            writeMore()
            writeObjectField("name")
            writeVal(name)
            guildId?.let { writeMore(); writeObjectField("guildId"); writeVal(it) }
            data?.let { writeMore(); writeObjectField("data"); writeVal(data) }
        }
    }
}