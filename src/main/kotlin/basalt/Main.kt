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