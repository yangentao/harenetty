@file:Suppress("unused")

package io.github.yangentao.harenetty

import io.github.yangentao.hare.HttpApp
import io.github.yangentao.types.MB
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import io.netty.handler.stream.ChunkedWriteHandler
import kotlin.reflect.KClass

class NettyHttpServer(
    val app: HttpApp, port: Int, bossCount: Int = 6,
    workerCount: Int = 12, val maxContentSize: Int = 100 * MB
) : BaseNettyServer(port, bossCount = bossCount, workerCount = workerCount) {

    private val httpHandler = HttpHandler(app)

    override fun onStart() {
    }

    override fun afterStart() {
    }

    override fun onStop() {
        app.destroy()
    }

    @Suppress("UNCHECKED_CAST")
    override fun initChannels(ch: SocketChannel) {
        ch.addLast(HttpServerCodec())
        ch.addLast(HttpObjectAggregator(maxContentSize, true))
        ch.addLast(ChunkedWriteHandler())
        for ((uri, paths) in app.webSockets) {
            ch.addLast(WebSocketServerProtocolHandler(uri, true))
            ch.addLast(WebSocketHandler(uri, paths as Map<String, KClass<out WebSocketEndpoint>>))
        }
        ch.addLast(httpHandler)
    }

}