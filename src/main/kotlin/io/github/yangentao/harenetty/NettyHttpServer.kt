@file:Suppress("unused")

package io.github.yangentao.harenetty

import io.github.yangentao.hare.HttpApp
import io.github.yangentao.types.MB
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import io.netty.handler.stream.ChunkedWriteHandler

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
        for ((uri, paths) in app.websocketMap) {
            val wc = WebSocketServerProtocolConfig.newBuilder()
                .websocketPath(uri)
                .subprotocols(null)
                .checkStartsWith(true)
                .handshakeTimeoutMillis(10_000)
                .dropPongFrames(false)
                .build()
            ch.addLast(WebSocketServerProtocolHandler(wc))
            ch.addLast(WebSocketHandler(uri, paths))
        }
        ch.addLast(httpHandler)
    }

}