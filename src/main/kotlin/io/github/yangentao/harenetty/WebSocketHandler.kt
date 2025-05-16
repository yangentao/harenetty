@file:Suppress("unused")

package io.github.yangentao.harenetty

import io.github.yangentao.hare.utils.UriPath
import io.github.yangentao.types.PatternText
import io.github.yangentao.types.createInstanceArgOne
import io.github.yangentao.xlog.logd
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.HandshakeComplete
import kotlin.reflect.KClass

//

private var ChannelHandlerContext.websocketEndpoint: WebSocketEndpoint? by ChannelProperties


class WebSocketHandler(val uri: String, val endpointMap: Map<String, KClass<out WebSocketEndpoint>>) : SimpleChannelInboundHandler<WebSocketFrame>() {

    private fun onOpen(context: ChannelHandlerContext, requestUri: String, headers: HttpHeaders) {
        val decoder = QueryStringDecoder(requestUri)
        val reqUri = decoder.path()
        val subpath = UriPath(reqUri).trimStartPath(uri)
        if (subpath == null) {
            return
        }
        val map = LinkedHashMap<String, String>()
        for (e in decoder.parameters()) {
            map[e.key] = e.value.first()
        }
        var instClass: KClass<out WebSocketEndpoint>? = null
        if (subpath.isEmpty()) {
            instClass = endpointMap[subpath]
        } else {
            for (e in endpointMap.entries) {
                val m = PatternText(e.key).tryMatchEntire(subpath)
                if (m != null) {
                    instClass = e.value
                    map.putAll(m)
                    break
                }
            }
        }
        if (instClass == null) {
            context.close()
            return
        }

        val wscontext = WebSocketContext(context, requestUri, map)
        val inst: WebSocketEndpoint = instClass.createInstanceArgOne(wscontext)!! as WebSocketEndpoint;
        context.websocketEndpoint = inst
        inst.onOpen(subpath)
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: WebSocketFrame) {
        when (msg) {
            is TextWebSocketFrame -> ctx.websocketEndpoint?.onTextMessage(msg.text())
            is BinaryWebSocketFrame -> ctx.websocketEndpoint?.onBinaryMessage(msg.content().bytesCopy)
            is PongWebSocketFrame -> ctx.websocketEndpoint?.onPongMessage(msg.content().bytesCopy)
        }
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is HandshakeComplete) {
            onOpen(ctx, evt.requestUri(), evt.requestHeaders())
            return
        }
        super.userEventTriggered(ctx, evt)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        ctx.websocketEndpoint?.onClose()
        ctx.websocketEndpoint = null
        super.channelInactive(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logd(cause)
        ctx.websocketEndpoint?.onError(cause)
        ctx.close()
    }
}

