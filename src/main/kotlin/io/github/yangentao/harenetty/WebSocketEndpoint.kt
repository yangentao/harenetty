package io.github.yangentao.harenetty

import io.github.yangentao.xlog.logd
import io.netty.channel.ChannelHandlerContext

abstract class WebSocketEndpoint(val context: ChannelHandlerContext) {
    var ident: String? = null

    open fun onOpen(uri: String, params: Map<String, String>) {
        logd("onOpen: $uri,  ident: $ident  ")
    }

    open fun onMessage(msg: String) {
        logd("onMessage: ident: ", ident, " msg: ", msg, " uri: ")
        context.channel().writeAndFlush("echo: $msg".websocketText)
    }

    open fun onBinary(message: ByteArray) {}
    open fun onPong(message: ByteArray) {}

    open fun onClose() {
    }

    open fun onError(cause: Throwable) {
    }

}