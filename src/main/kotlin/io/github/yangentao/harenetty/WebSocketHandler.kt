@file:Suppress("unused")

package io.github.yangentao.harenetty

import io.github.yangentao.hare.utils.UriPath
import io.github.yangentao.types.PatternText
import io.github.yangentao.types.createInstanceArgOne
import io.github.yangentao.types.createInstanceX
import io.github.yangentao.types.invokeMap
import io.github.yangentao.xlog.logd
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.HandshakeComplete
import jdk.internal.joptsimple.internal.Messages.message
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.primaryConstructor

//
abstract class WebSocketEndpoint(val context: ChannelHandlerContext) {
    var ident: String? = null

    open fun onOpen(uri: String) {
        logd("onOpen: $uri,  ident: $ident  ")
    }

    open fun onMessage(msg: String) {
        logd("onMessage: ident: ", ident, " msg: ", msg, " uri: ")
        context.channel().writeAndFlush("echo: $msg".websocketText)
    }

    open fun onClose() {
    }

    open fun onError() {
    }

}

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

class WebSocketHandler(val uri: String, val endpointMap: Map<String, KClass<out WebSocketEndpoint>>) : SimpleChannelInboundHandler<TextWebSocketFrame>() {

    private fun onOpen(context: ChannelHandlerContext, requestUri: String, headers: HttpHeaders) {
        val decoder = QueryStringDecoder(requestUri)
        val reqUri = decoder.path()
        val path = UriPath(reqUri).trimStartPath(uri)
        if (path == null) {
            return
        }
        var argMap: Map<String, String>? = null
        lateinit var instClass: KClass<out WebSocketEndpoint>
        for (e in endpointMap.entries) {
            argMap = PatternText(e.key).tryMatchEntire(path)
            if (argMap != null) {
                instClass = e.value
                break
            }
        }
        if (argMap == null) {
            context.close()
            return
        }
        val map = LinkedHashMap<String, String>()
        for (e in decoder.parameters()) {
            map[e.key] = e.value.first()
        }
        for (e in argMap) {
            map[e.key] = e.value
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
        inst.onOpen(requestUri)
//        context.invokeWebSocketEndpoint("onOpen", map, typeList = listOf(context, this, headers))
    }

    private fun onClose(context: ChannelHandlerContext) {
        context.invokeWebSocketEndpoint("onClose", emptyMap(), typeList = listOf(context, this))
        context.websocketEndpoint = null
        context.websocketArguments = null
    }

    private fun onMessage(context: ChannelHandlerContext, message: String) {
        context.websocketEndpoint?.onMessage(message)
    }

    private fun onError(context: ChannelHandlerContext, cause: Throwable) {
        context.websocketEndpoint?.onError()
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: TextWebSocketFrame) {
        onMessage(ctx, msg.text())
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is HandshakeComplete) {
            onOpen(ctx, evt.requestUri(), evt.requestHeaders())
            return
        }
        super.userEventTriggered(ctx, evt)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        onClose(ctx)
        super.channelInactive(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logd(cause)
        onError(ctx, cause)
        ctx.close()
    }
}

val String.websocketText: TextWebSocketFrame get() = TextWebSocketFrame(this)
