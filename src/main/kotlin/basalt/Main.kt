package basalt

import io.vertx.core.Vertx

fun main(args: Array<String>) {
    val vertx = Vertx.vertx()
    vertx.deployVerticle("basalt.server.BasaltServer")
    Runtime.getRuntime().addShutdownHook(Thread {
        vertx.close()
    })
}