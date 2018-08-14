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

import basalt.server.BasaltServer
import com.jsoniter.annotation.JsonIgnore
import oshi.SystemInfo
import java.lang.management.ManagementFactory

/* Extreme amounts of credit to the Lavalink Developers here. The statistics presented here are essentially
   the same in every single possibly category (with the notable loss of audio statistics, which aren't present yet).

   Credit goes mainly to Frederikam, Shredder121 and Napster. Original source code can be found here:
   https://github.com/Frederikam/Lavalink/blob/master/LavalinkServer/src/main/java/lavalink/server/io/StatsTask.java
*/
private val sysInfo = SystemInfo()

@Suppress("UNUSED")
class StatsUpdate internal constructor(@field:JsonIgnore private val server: BasaltServer) {
    val op = "statsUpdate"
    var players = 0
    var playingPlayers = 0
    val uptime = ManagementFactory.getRuntimeMXBean().uptime
    val memory = Memory()
    val cpu = Cpu()

    init {
        for (context in server.contexts.values) {
            for (player in context.players.values) {
                players++
                if (!player.audioPlayer.isPaused && player.audioPlayer.playingTrack != null)
                    playingPlayers++
            }
        }
    }

    inner class Memory {
        @JsonIgnore private val runtime = Runtime.getRuntime()
        val free = runtime.freeMemory()
        val used = runtime.totalMemory() - free
        val allocated = runtime.totalMemory()
        val reserved = runtime.maxMemory()
    }

    inner class Cpu {
        val cores = Runtime.getRuntime().availableProcessors()
        val systemLoad = sysInfo.hardware.processor.systemCpuLoad
        val basaltLoad = getBasaltProcessLoad()

        private fun getBasaltProcessLoad(): Double {
            val os = sysInfo.operatingSystem
            val process = os.getProcess(os.processId)
            return process.calculateCpuPercent()
        }
    }
}