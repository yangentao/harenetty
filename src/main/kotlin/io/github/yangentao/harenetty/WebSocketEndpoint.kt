@file:Suppress("unused")

package io.github.yangentao.harenetty

import io.github.yangentao.hare.HttpApp
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import kotlin.reflect.KClass

/// websocket("/ws", "dev/{ident}", DevEndpoint::class)
/// websocket("/ws", "data/{ident}", DataEndpoint::class)
fun HttpApp.websocket(uri: String, subpath: String, endpoint: KClass<out WebSocketEndpoint>) {
    val m = websocketMap.getOrPut(uri) { LinkedHashMap() }
    m[subpath] = endpoint
}

internal typealias WebSocketMap = LinkedHashMap<String, LinkedHashMap<String, KClass<out WebSocketEndpoint>>>

internal val HttpApp.websocketMap: WebSocketMap
    get() {
        var map: WebSocketMap? = this.attrStore.get("websocketMap")
        if (map != null) return map
        map = WebSocketMap()
        this.attrStore.set("websocketMap", map)
        return map
    }

class WebSocketContext(val context: ChannelHandlerContext, val requestUri: String, val params: Map<String, String>) {

    fun sendText(message: String) {
        val m = TextWebSocketFrame(message)
        context.channel().writeAndFlush(m)
    }

    fun sendBinary(message: ByteArray) {
        val m = BinaryWebSocketFrame(Unpooled.wrappedBuffer(message))
        context.channel().writeAndFlush(m)
    }

    fun sendPong(message: ByteArray) {
        val m = PongWebSocketFrame(Unpooled.wrappedBuffer(message))
        context.channel().writeAndFlush(m)
    }

    fun sendPing(message: ByteArray) {
        val m = PingWebSocketFrame(Unpooled.wrappedBuffer(message))
        context.channel().writeAndFlush(m)
    }
}

abstract class WebSocketEndpoint(val context: WebSocketContext) {

    abstract fun onOpen(subpath: String)

    abstract fun onTextMessage(msg: String)

    open fun onBinaryMessage(message: ByteArray) {

    }

    open fun onPongMessage(message: ByteArray) {

    }

    open fun onClose() {
    }

    open fun onError(cause: Throwable) {
    }

}