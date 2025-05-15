@file:Suppress("unused")

package io.github.yangentao.harenetty

import io.github.yangentao.hare.utils.UriPath
import io.github.yangentao.types.PatternText
import io.github.yangentao.types.createInstanceArgOne
import io.github.yangentao.types.invokeMap
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
import kotlin.reflect.full.declaredMemberFunctions

//

private var ChannelHandlerContext.websocketEndpoint: WebSocketEndpoint? by ChannelProperties
private var ChannelHandlerContext.websocketArguments: Map<String, String>? by ChannelProperties

private fun ChannelHandlerContext.invokeWebSocketEndpoint(funName: String, nameMap: Map<String, Any>, typeList: List<Any>) {
    val inst = this.websocketEndpoint ?: return
    val func = inst::class.declaredMemberFunctions.firstOrNull { it.name == funName } ?: return
    val map = LinkedHashMap<String, Any>()
    val argMap = this.websocketArguments
    if (argMap != null) map.putAll(argMap)
    map.putAll(nameMap)

    func.invokeMap(inst = inst, nameMap = map, typeList = typeList)
}

class DefaultWebSocketService() {

}

class WebSocketHandler(val uri: String, val endpointMap: Map<String, KClass<out WebSocketEndpoint>>) : SimpleChannelInboundHandler<WebSocketFrame>() {

    private fun onOpen(context: ChannelHandlerContext, requestUri: String, headers: HttpHeaders) {
        val decoder = QueryStringDecoder(requestUri)
        val reqUri = decoder.path()
        val path = UriPath(reqUri).trimStartPath(uri)
        if (path == null) {
            return
        }

        var argMap: Map<String, String>? = null
        lateinit var instClass: KClass<out WebSocketEndpoint>
        if (path.isEmpty()) {
            val cls = endpointMap[path]
            if (cls == null) {
                context.close()
                return
            }
            instClass = cls
        } else {
            for (e in endpointMap.entries) {
                argMap = PatternText(e.key).tryMatchEntire(path)
                if (argMap != null) {
                    instClass = e.value
                    break
                }
            }
        }

        val map = LinkedHashMap<String, String>()
        for (e in decoder.parameters()) {
            map[e.key] = e.value.first()
        }
        if (argMap != null) {
            for (e in argMap) {
                map[e.key] = e.value
            }
        }
        if (!map.containsKey("uri")) {
            map["uri"] = path
        }
        if (!map.containsKey("requestUri")) {
            map["requestUri"] = requestUri
        }
        val inst: WebSocketEndpoint = instClass.createInstanceArgOne(context)!! as WebSocketEndpoint;
        context.websocketEndpoint = inst
        context.websocketArguments = map
        inst.onOpen(requestUri, map)
//        context.invokeWebSocketEndpoint("onOpen", map, typeList = listOf(context, this, headers))
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: WebSocketFrame) {
        when (msg) {
            is TextWebSocketFrame -> ctx.websocketEndpoint?.onMessage(msg.text())
            is BinaryWebSocketFrame -> ctx.websocketEndpoint?.onBinary(msg.content().bytesCopy)
            is PongWebSocketFrame -> ctx.websocketEndpoint?.onPong(msg.content().bytesCopy)
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
        ctx.invokeWebSocketEndpoint("onClose", emptyMap(), typeList = listOf(ctx, this))
        ctx.websocketEndpoint = null
        ctx.websocketArguments = null
        super.channelInactive(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logd(cause)
        ctx.websocketEndpoint?.onError(cause)
        ctx.close()
    }
}

val String.websocketText: TextWebSocketFrame get() = TextWebSocketFrame(this)
