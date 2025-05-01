@file:Suppress("unused")

package io.github.yangentao.harenetty

import io.github.yangentao.hare.log.logd

class GroupNettyServer(val services: List<BaseNettyServer>) {

    private val onShutdown = Thread {
        stop()
    }

    fun start(): GroupNettyServer {
        for (h in services) {
            h.start()
            logd("Listen on: ", h.port)
        }
        Runtime.getRuntime().addShutdownHook(onShutdown)
        logd("Server started.")
        return this
    }

    fun stop(): GroupNettyServer {
        for (h in services) {
            h.stop()
        }
        return this
    }

    fun waitClose(): GroupNettyServer {
        for (h in services) {
            h.waitClose()
        }
        return this
    }
}