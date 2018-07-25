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
package basalt

import io.vertx.core.Vertx

/**
 * Main entry point into Basalt, which deploys a [BasaltServer][basalt.server.BasaltServer] Verticle and adds a shutdown hook.
 * @param args The command-line arguments which don't serve a purpose in Basalt right now.
 */
fun main(args: Array<String>) {
    val vertx = Vertx.vertx()
    vertx.deployVerticle("basalt.server.BasaltServer")
    Runtime.getRuntime().addShutdownHook(Thread {
        vertx.close()
    })
}